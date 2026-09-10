package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleInvoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoiceId"])]
)
data class SaleInvoiceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceId: Long,
    val productId: Long? = null,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val subtotal: Double, // quantity * unitPrice
    val unit: String = "حبة"
)
