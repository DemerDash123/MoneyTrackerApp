package com.moneytracker.app.core

import com.moneytracker.app.i18n.S
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * All money in the app is stored as a [Long] count of *milli-units*
 * (1/1000 of the main currency unit). For Omani Rial this is exactly one baisa.
 * Integers never lose precision, so sums and differences are always exact.
 * The configured number of decimals (0, 2 or 3) only affects input and display.
 */
object MoneyMath {
    const val SCALE = 3
    const val UNIT = 1000L

    /** Largest single amount accepted: 1,000,000,000 units. */
    const val MAX_AMOUNT = 1_000_000_000_000L

    fun fromUnits(units: Long): Long = units * UNIT
}

sealed interface AmountParse {
    data class Ok(val milli: Long) : AmountParse
    data class Error(val message: String) : AmountParse
    data object Empty : AmountParse
}

/**
 * @param isolate wrap formatted amounts in Unicode LTR isolates so they read correctly
 *                inside right-to-left (Arabic) text, e.g. the minus sign stays in front.
 */
class MoneyFormatter(val currency: String, val decimals: Int, val isolate: Boolean = false) {

    init {
        require(decimals in 0..3) { "decimals must be 0..3" }
    }

    private val pattern = if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)

    private fun decimalFormat() = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))

    private fun rounded(milli: Long): BigDecimal =
        BigDecimal.valueOf(milli, MoneyMath.SCALE).setScale(decimals, RoundingMode.HALF_UP)

    /** "1,234.500" */
    fun number(milli: Long): String = decimalFormat().format(rounded(milli))

    /** "1,234.500 OMR" (negative values keep their minus sign). */
    fun format(milli: Long): String =
        ltr(if (currency.isBlank()) number(milli) else "${number(milli)} $currency")

    /** "+12.000 OMR" or "−12.000 OMR" using the sign given by [positive]. */
    fun signed(milli: Long, positive: Boolean): String {
        val plain = (if (positive) "+" else "\u2212") + number(abs(milli)) + if (currency.isBlank()) "" else " $currency"
        return ltr(plain)
    }

    private fun ltr(s: String) = if (isolate) "\u2066$s\u2069" else s

    /** Plain value used to pre-fill an amount input when editing: "12.5". */
    fun input(milli: Long): String {
        val plain = rounded(milli).stripTrailingZeros().toPlainString()
        return if (plain == "0.0") "0" else plain
    }

    /** Short label for chart axes: 950, 1.2K, 3.4M. */
    fun compact(milli: Long): String {
        val value = milli / MoneyMath.UNIT.toDouble()
        val a = abs(value)
        fun one(v: Double): String =
            String.format(Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')
        return when {
            a >= 1_000_000 -> one(value / 1_000_000) + "M"
            a >= 1_000 -> one(value / 1_000) + "K"
            a >= 10 || decimals == 0 -> String.format(Locale.US, "%.0f", value)
            a == 0.0 -> "0"
            else -> one(value)
        }
    }

    /**
     * Parses user input into milli-units. Accepts "12", "12.5", "1,250.750",
     * a single comma as decimal separator ("12,5") and Arabic-Indic digits.
     */
    fun parse(raw: String): AmountParse {
        var s = normalizeDigits(raw.trim()).replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return AmountParse.Empty
        s = when {
            s.contains('.') -> s.replace(",", "")
            s.count { it == ',' } == 1 && s.substringAfter(',').length != 3 -> s.replace(',', '.')
            else -> s.replace(",", "")
        }
        if (s.startsWith("-")) return AmountParse.Error(S.amountGtZero)
        if (!NUMBER.matches(s)) return AmountParse.Error(S.invalidAmount)
        return try {
            val bd = BigDecimal(s)
            if (bd.signum() <= 0) return AmountParse.Error(S.amountGtZero)
            if (bd.stripTrailingZeros().scale() > decimals) {
                return AmountParse.Error(
                    if (decimals == 0) S.wholeOnly else S.maxDecimals(decimals)
                )
            }
            val milli = bd.movePointRight(MoneyMath.SCALE).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
            if (milli > MoneyMath.MAX_AMOUNT) AmountParse.Error(S.amountTooLarge) else AmountParse.Ok(milli)
        } catch (e: ArithmeticException) {
            AmountParse.Error(S.amountTooLarge)
        } catch (e: NumberFormatException) {
            AmountParse.Error(S.invalidAmount)
        }
    }

    companion object {
        private val NUMBER = Regex("^(\\d+(\\.\\d*)?|\\.\\d+)$")

        fun normalizeDigits(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) {
                sb.append(
                    when (c) {
                        in '٠'..'٩' -> '0' + (c - '٠')
                        in '۰'..'۹' -> '0' + (c - '۰')
                        '٫' -> '.'
                        '٬' -> ','
                        else -> c
                    }
                )
            }
            return sb.toString()
        }
    }
}
