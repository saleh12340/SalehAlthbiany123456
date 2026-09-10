package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customer_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"])]
)
data class CustomerTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val date: String,
    val time: String,
    val type: String, // فاتورة مبيعات, سند قبض, دفعة, رصيد افتتاحي
    val description: String,
    val amount: Double = 0.0, // Value of sale/invoice added to debt
    val paid: Double = 0.0, // Payment reducing debt
    val remaining: Double = 0.0, // Running balance or transaction remaining
    val invoiceId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
