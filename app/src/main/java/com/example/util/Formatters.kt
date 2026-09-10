package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

object Formatters {
    private val decimalFormat = DecimalFormat("#,##0.##")
    private val currencyFormat = DecimalFormat("#,##0.00")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)

    fun formatMoney(amount: Double): String {
        return "${currencyFormat.format(amount)} ر.ي"
    }

    fun formatNumber(number: Double): String {
        return decimalFormat.format(number)
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
        val timestamp = SimpleDateFormat("yyMMdd", Locale.ENGLISH).format(Date())
        val numberPart = String.format(Locale.ENGLISH, "%03d", (count % 1000) + 1)
        return "$prefix-$timestamp-$numberPart"
    }
}
