package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.GroceryDao
import com.example.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Database(
    entities = [
        Product::class,
        Customer::class,
        Supplier::class,
        SaleInvoice::class,
        SaleInvoiceItem::class,
        PurchaseInvoice::class,
        PurchaseInvoiceItem::class,
        CustomerTransaction::class,
        SupplierTransaction::class,
        Expense::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groceryDao(): GroceryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "al_ezzi_grocery_database"
                )
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.groceryDao())
                    }
                }
            }

            private suspend fun populateInitialData(dao: GroceryDao) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
                val today = dateFormat.format(Date())
                val nowTime = timeFormat.format(Date())

                // 1. Initial Products
                val initialProducts = listOf(
                    Product(name = "أرز الشعلان 5 كجم", barcode = "6281001", price = 4200.0, costPrice = 3700.0, quantity = 25.0, minStock = 5.0, unit = "كيس", category = "أرز وحبوب"),
                    Product(name = "سكر الأسرة 10 كجم", barcode = "6281002", price = 6500.0, costPrice = 5800.0, quantity = 18.0, minStock = 4.0, unit = "كيس", category = "سكر وزيوت"),
                    Product(name = "زيت عافية ذرة 1.5 لتر", barcode = "6281003", price = 2100.0, costPrice = 1850.0, quantity = 30.0, minStock = 6.0, unit = "حبة", category = "سكر وزيوت"),
                    Product(name = "حليب الممتاز بودرة 900 جرام", barcode = "6281004", price = 3400.0, costPrice = 3000.0, quantity = 12.0, minStock = 3.0, unit = "علبة", category = "ألبان ومشتقاتها"),
                    Product(name = "شاي الكبوس أحمر 227 جرام", barcode = "6281005", price = 950.0, costPrice = 800.0, quantity = 40.0, minStock = 8.0, unit = "باكت", category = "مشروبات وشاي"),
                    Product(name = "تونة ريم خفيفة 185 جرام", barcode = "6281006", price = 750.0, costPrice = 620.0, quantity = 4.0, minStock = 10.0, unit = "علبة", category = "معلبات"), // Low stock
                    Product(name = "زبادي الهناء 170 جرام", barcode = "6281007", price = 300.0, costPrice = 240.0, quantity = 50.0, minStock = 12.0, unit = "حبة", category = "ألبان ومشتقاتها"),
                    Product(name = "مكرونة قودي 400 جرام", barcode = "6281008", price = 450.0, costPrice = 360.0, quantity = 35.0, minStock = 8.0, unit = "كيس", category = "معلبات ومعكرونة"),
                    Product(name = "صابون تايد غسيل 2.5 كجم", barcode = "6281009", price = 3200.0, costPrice = 2750.0, quantity = 15.0, minStock = 3.0, unit = "كيس", category = "منظفات"),
                    Product(name = "ماء هناء كرتون 330 مل", barcode = "6281010", price = 1800.0, costPrice = 1500.0, quantity = 20.0, minStock = 5.0, unit = "كرتون", category = "مياه ومشروبات")
                )
                dao.insertProducts(initialProducts)

                // 2. Initial Customers
                val initialCustomers = listOf(
                    Customer(name = "محمد أحمد العنسي", phone = "771234567", address = "شارع الستين", balance = 8500.0, totalSales = 15000.0, totalPaid = 6500.0, notes = "عميل حساب شهري"),
                    Customer(name = "علي عبد الله الحداد", phone = "772345678", address = "حي الصافية", balance = 3200.0, totalSales = 7200.0, totalPaid = 4000.0, notes = "عميل دائم"),
                    Customer(name = "سالم صالح العامري", phone = "773456789", address = "شارع حدة", balance = 0.0, totalSales = 12000.0, totalPaid = 12000.0, notes = "دفع فوري نقدي")
                )
                dao.insertCustomers(initialCustomers)

                // 3. Initial Suppliers
                val initialSuppliers = listOf(
                    Supplier(name = "مؤسسة الأمل للمواد الغذائية", phone = "770112233", company = "الأمل للتجارة", address = "شارع تعز", balance = 24000.0, totalPurchases = 120000.0, totalPaid = 96000.0),
                    Supplier(name = "شركة النجم للألبان والمشروبات", phone = "770445566", company = "النجم الذهبي", address = "المنطقة الصناعية", balance = 15000.0, totalPurchases = 45000.0, totalPaid = 30000.0)
                )
                dao.insertSuppliers(initialSuppliers)

                // 4. Initial Expenses
                val initialExpenses = listOf(
                    Expense(date = today, time = "09:30", title = "فاتورة كهرباء المحل", amount = 3500.0, category = "خدمات وكهرباء", note = "سداد كرت كهرباء تجاري"),
                    Expense(date = today, time = "11:15", title = "أكياس تغليف وبلاستيك", amount = 1200.0, category = "أدوات ومستهلكات", note = "أكياس تسوق للمحل")
                )
                dao.insertExpenses(initialExpenses)

                // 5. Initial Sale Invoice
                val invoiceId = dao.insertSaleInvoice(
                    SaleInvoice(
                        invoiceNumber = "INV-1001",
                        date = today,
                        time = nowTime,
                        customerId = 1L,
                        customerName = "محمد أحمد العنسي",
                        customerPhone = "771234567",
                        subtotal = 6300.0,
                        discount = 300.0,
                        grandTotal = 6000.0,
                        paidAmount = 2000.0,
                        remainingAmount = 4000.0,
                        paymentMethod = "آجل",
                        notes = "فاتورة افتتاحية"
                    )
                )

                val invoiceItems = listOf(
                    SaleInvoiceItem(invoiceId = invoiceId, productId = 1L, productName = "أرز الشعلان 5 كجم", quantity = 1.0, unitPrice = 4200.0, subtotal = 4200.0, unit = "كيس"),
                    SaleInvoiceItem(invoiceId = invoiceId, productId = 3L, productName = "زيت عافية ذرة 1.5 لتر", quantity = 1.0, unitPrice = 2100.0, subtotal = 2100.0, unit = "حبة")
                )
                dao.insertSaleInvoiceItems(invoiceItems)

                dao.insertCustomerTransaction(
                    CustomerTransaction(
                        customerId = 1L,
                        date = today,
                        time = nowTime,
                        type = "فاتورة مبيعات",
                        description = "فاتورة رقم INV-1001",
                        amount = 6000.0,
                        paid = 2000.0,
                        remaining = 4000.0,
                        invoiceId = invoiceId
                    )
                )
            }
        }
    }
}
