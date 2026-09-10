package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sale_invoices")
data class SaleInvoice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val customerId: Long? = null,
    val customerName: String = "عميل نقدي",
    val customerPhone: String = "",
    val subtotal: Double,
    val discount: Double = 0.0,
    val grandTotal: Double,
    val paidAmount: Double,
    val remainingAmount: Double, // grandTotal - paidAmount
    val paymentMethod: String = "نقدي", // نقدي, آجل, تحويل, أخرى
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
