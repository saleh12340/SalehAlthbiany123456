package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchase_invoices")
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String,
    val date: String,
    val time: String,
    val supplierId: Long? = null,
    val supplierName: String = "مورد نقدي",
    val subtotal: Double,
    val discount: Double = 0.0,
    val grandTotal: Double,
    val paidAmount: Double,
    val remainingAmount: Double,
    val paymentMethod: String = "نقدي",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
