package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.PostSaveShareBanner
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GroceryViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GroceryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                RequestBluetoothPermissions()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val postSaveBannerData by viewModel.postSaveShareBanner.collectAsState()
                        NavHost(navController = navController, startDestination = "splash") {
                            composable("splash") {
                                SplashScreen {
                                    navController.navigate("home") { popUpTo("splash") { inclusive = true } }
                                }
                            }
                            composable("home") {
                                HomeScreen(viewModel = viewModel, onNavigate = { route -> navController.navigate(route) })
                            }
                            composable("sales") {
                                SalesInvoicesScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCreate = { editId ->
                                        navController.navigate(if (editId != null && editId > 0) "create_sale?editId=$editId" else "create_sale")
                                    }
                                )
                            }
                            composable(
                                route = "create_sale?editId={editId}",
                                arguments = listOf(navArgument("editId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                })
                            ) { entry ->
                                CreateInvoiceScreen(
                                    viewModel = viewModel,
                                    editingInvoiceId = entry.arguments?.getString("editId")?.toLongOrNull(),
                                    onNavigateBack = { navController.popBackStack() },
                                    onInvoiceCreated = { navController.popBackStack() }
                                )
                            }
                            composable("create_sale") {
                                CreateInvoiceScreen(
                                    viewModel = viewModel,
                                    editingInvoiceId = null,
                                    onNavigateBack = { navController.popBackStack() },
                                    onInvoiceCreated = { navController.popBackStack() }
                                )
                            }
                            composable("products") {
                                ProductsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToEditSaleInvoice = { id -> navController.navigate("create_sale?editId=$id") },
                                    onNavigateToEditPurchaseInvoice = { id -> navController.navigate("purchases?editId=$id") }
                                )
                            }
                            composable("customers") {
                                CustomersScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToEditInvoice = { id -> navController.navigate("create_sale?editId=$id") }
                                )
                            }
                            composable(
                                route = "purchases?editId={editId}",
                                arguments = listOf(navArgument("editId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                })
                            ) { entry ->
                                PurchasesScreen(
                                    viewModel = viewModel,
                                    editingInvoiceId = entry.arguments?.getString("editId")?.toLongOrNull(),
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("suppliers") {
                                SuppliersScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToEditPurchaseInvoice = { id -> navController.navigate("purchases?editId=$id") }
                                )
                            }
                            composable("expenses") {
                                ExpensesScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                            }
                            composable("reports") {
                                ReportsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToEditSaleInvoice = { id -> navController.navigate("create_sale?editId=$id") },
                                    onNavigateToEditPurchaseInvoice = { id -> navController.navigate("purchases?editId=$id") }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().padding(bottom = 90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PostSaveShareBanner(
                                data = postSaveBannerData,
                                onDismiss = { viewModel.clearPostSaveShare() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestBluetoothPermissions() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
}
