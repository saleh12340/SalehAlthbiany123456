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
) {
    /** Compatibility constructor for invoice-entry screens that create items before the invoice ID exists. */
    constructor(
        id: Long,
        productId: Long?,
        productName: String,
        quantity: Double,
        unitPrice: Double,
        subtotal: Double,
        unit: String
    ) : this(id, 0L, productId, productName, quantity, unitPrice, subtotal, unit)
}
