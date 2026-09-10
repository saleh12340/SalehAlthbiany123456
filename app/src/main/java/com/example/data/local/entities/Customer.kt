package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val balance: Double = 0.0, // Positive: owes money (debt/debit), Negative: credit
    val totalSales: Double = 0.0,
    val totalPaid: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
