package com.moneytracker.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon choices for categories. The key is what gets stored in the database. */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "savings" to Icons.Rounded.Savings,
        "restaurant" to Icons.Rounded.Restaurant,
        "grocery" to Icons.Rounded.LocalGroceryStore,
        "shopping" to Icons.Rounded.ShoppingBag,
        "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "bus" to Icons.Rounded.DirectionsBus,
        "school" to Icons.Rounded.School,
        "book" to Icons.Rounded.Book,
        "emergency" to Icons.Rounded.HealthAndSafety,
        "medical" to Icons.Rounded.MedicalServices,
        "movie" to Icons.Rounded.Movie,
        "games" to Icons.Rounded.SportsEsports,
        "phone" to Icons.Rounded.PhoneAndroid,
        "laptop" to Icons.Rounded.Laptop,
        "flight" to Icons.Rounded.Flight,
        "travel" to Icons.Rounded.Luggage,
        "beach" to Icons.Rounded.BeachAccess,
        "home" to Icons.Rounded.Home,
        "bills" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "water" to Icons.Rounded.WaterDrop,
        "clothes" to Icons.Rounded.Checkroom,
        "cafe" to Icons.Rounded.LocalCafe,
        "fastfood" to Icons.Rounded.Fastfood,
        "gift" to Icons.Rounded.CardGiftcard,
        "celebration" to Icons.Rounded.Celebration,
        "charity" to Icons.Rounded.VolunteerActivism,
        "fitness" to Icons.Rounded.FitnessCenter,
        "sports" to Icons.Rounded.SportsSoccer,
        "pets" to Icons.Rounded.Pets,
        "child" to Icons.Rounded.ChildCare,
        "work" to Icons.Rounded.Work,
        "tools" to Icons.Rounded.Handyman,
        "bank" to Icons.Rounded.AccountBalance,
        "payments" to Icons.Rounded.Payments,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "music" to Icons.Rounded.MusicNote,
        "camera" to Icons.Rounded.PhotoCamera,
        "diamond" to Icons.Rounded.Diamond,
        "spa" to Icons.Rounded.Spa,
        "heart" to Icons.Rounded.Favorite,
        "star" to Icons.Rounded.Star,
        "umbrella" to Icons.Rounded.Umbrella,
        "category" to Icons.Rounded.Category,
    )

    private val map = all.toMap()

    fun get(key: String?): ImageVector = map[key] ?: Icons.Rounded.Category

    val colors: List<Int> = listOf(
        0xFF10B981, 0xFF3B82F6, 0xFFF59E0B, 0xFFEF4444, 0xFF8B5CF6, 0xFFEC4899,
        0xFF14B8A6, 0xFFF97316, 0xFF6366F1, 0xFF84CC16, 0xFF06B6D4, 0xFF64748B,
        0xFFA855F7, 0xFFEAB308, 0xFF0EA5E9, 0xFFBE185D,
    ).map { it.toInt() }

    /** Suggested starter categories (created only when the user taps them). */
    data class Suggestion(val name: String, val icon: String, val color: Int)

    private val suggestionStyles = listOf(
        "savings" to 0xFF10B981, "restaurant" to 0xFFF59E0B, "shopping" to 0xFFEC4899,
        "car" to 0xFF3B82F6, "school" to 0xFF8B5CF6, "emergency" to 0xFFEF4444,
        "movie" to 0xFF6366F1, "bills" to 0xFF14B8A6, "flight" to 0xFF06B6D4,
    )

    /** Starter categories, named in the current app language. */
    val suggestions: List<Suggestion>
        get() = com.moneytracker.app.i18n.S.suggestionNames.zip(suggestionStyles) { name, (icon, color) ->
            Suggestion(name, icon, color.toInt())
        }
}
