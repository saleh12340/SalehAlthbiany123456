package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val title: String,
    val amount: Double,
    val category: String = "عام", // فواتير كهرباء/ماء, إيجار, عمالة, نقل, صيانة, بضاعة تالفة, أخرى
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
