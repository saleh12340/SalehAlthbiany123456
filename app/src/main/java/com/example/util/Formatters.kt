package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

/**
 * Central formatting rules for the entire application.
 * Arabic UI remains RTL, while every numeric character is strictly Western 0-9.
 */
object Formatters {
    val englishSymbols = DecimalFormatSymbols(Locale.US)
    val decimalFormat = DecimalFormat("#,##0.##", englishSymbols)
    val currencyFormat = DecimalFormat("#,##0.00", englishSymbols)
    val integerFormat = DecimalFormat("#,##0", englishSymbols)
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    /** Converts Arabic-Indic (٠-٩) and Extended Arabic-Indic digits to English digits (0-9). */
    fun englishDigits(value: String): String = value.map { char ->
        when (char) {
            '\u0660' -> '0'
            '\u0661' -> '1'
            '\u0662' -> '2'
            '\u0663' -> '3'
            '\u0664' -> '4'
            '\u0665' -> '5'
            '\u0666' -> '6'
            '\u0667' -> '7'
            '\u0668' -> '8'
            '\u0669' -> '9'
            '\u06F0' -> '0'
            '\u06F1' -> '1'
            '\u06F2' -> '2'
            '\u06F3' -> '3'
            '\u06F4' -> '4'
            '\u06F5' -> '5'
            '\u06F6' -> '6'
            '\u06F7' -> '7'
            '\u06F8' -> '8'
            '\u06F9' -> '9'
            else -> char
        }
    }.joinToString("")

    fun formatMoney(amount: Double): String {
        return "${currencyFormat.format(amount)} ر.ي"
    }

    fun formatNumber(number: Double): String {
        return decimalFormat.format(number)
    }

    fun formatInt(number: Int): String {
        return integerFormat.format(number)
    }

    fun getTodayDate(): String {
        return dateFormat.format(Date())
    }

    fun getYesterdayDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DATE, -1)
        return dateFormat.format(calendar.time)
    }

    fun getWeekStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        return dateFormat.format(calendar.time)
    }

    fun getMonthStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        return dateFormat.format(calendar.time)
    }

    fun getYearStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        return dateFormat.format(calendar.time)
    }

    fun getCurrentTime(): String {
        return timeFormat.format(Date())
    }

    fun generateInvoiceNumber(prefix: String = "INV", count: Int): String {
        val timestamp = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val numberPart = String.format(Locale.US, "%03d", (count % 1000) + 1)
        return "$prefix-$timestamp-$numberPart"
    }
}
