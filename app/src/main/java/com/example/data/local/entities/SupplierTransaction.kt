package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplier_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supplierId"])]
)
data class SupplierTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val supplierId: Long,
    val date: String,
    val time: String,
    val type: String, // فاتورة مشتريات, سند صرف, دفعة للمورد, رصيد افتتاحي
    val description: String,
    val amount: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    val invoiceId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
