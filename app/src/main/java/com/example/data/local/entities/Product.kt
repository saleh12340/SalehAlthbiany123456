package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String = "",
    val price: Double,
    val costPrice: Double = 0.0,
    val quantity: Double,
    val minStock: Double = 5.0,
    val unit: String = "حبة",
    val category: String = "عام",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
