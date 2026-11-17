package com.example.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FamilyZoneOption(
    val zoneId: String,
    val label: String
)

object FamilyTimeZoneHelper {
    val commonZones: List<FamilyZoneOption> = listOf(
        FamilyZoneOption("Asia/Kathmandu", "Nepal (Kathmandu)"),
        FamilyZoneOption("Asia/Kolkata", "India (IST)"),
        FamilyZoneOption("Asia/Dhaka", "Bangladesh (Dhaka)"),
        FamilyZoneOption("Asia/Karachi", "Pakistan (Karachi)"),
        FamilyZoneOption("Asia/Dubai", "UAE / Gulf (Dubai)"),
        FamilyZoneOption("Asia/Riyadh", "Saudi / Qatar (Riyadh)"),
        FamilyZoneOption("Asia/Kuala_Lumpur", "Malaysia (KL)"),
        FamilyZoneOption("Europe/London", "UK (London)"),
        FamilyZoneOption("America/New_York", "USA East (EST)"),
        FamilyZoneOption("America/Los_Angeles", "USA West (PST)")
    )

    fun formatLocalTime(zoneId: String, localeCode: String = "en", nowMillis: Long = System.currentTimeMillis()): String {
        val tz = TimeZone.getTimeZone(zoneId)
        val normalized = AppLanguage.fromCode(localeCode).code
        val locale = Locale.Builder().setLanguage(normalized).build()
        val fmt = SimpleDateFormat("EEE, hh:mm a", locale).apply {
            timeZone = tz
        }
        return fmt.format(Date(nowMillis))
    }

    fun dayPartLabel(zoneId: String, nowMillis: Long = System.currentTimeMillis()): String {
        val tz = TimeZone.getTimeZone(zoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night / Quiet"
        }
    }

    fun zoneDisplayName(zoneId: String): String =
        commonZones.firstOrNull { it.zoneId == zoneId }?.label ?: zoneId
}
