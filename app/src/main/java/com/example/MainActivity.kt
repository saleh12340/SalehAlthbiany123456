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
import androidx.compose.material3.Surface
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
                // Request Bluetooth runtime permissions if needed
                RequestBluetoothPermissions()

                // Enforce RTL across all UI
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val postSaveBannerData by viewModel.postSaveShareBanner.collectAsState()

                        NavHost(
                            navController = navController,
                            startDestination = "splash"
                        ) {
                            composable("splash") {
                                SplashScreen(
                                    onSplashFinished = {
                                        navController.navigate("home") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigate = { route -> navController.navigate(route) }
                                )
                            }

                            composable("sales") {
                                SalesInvoicesScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToCreate = { editId ->
                                        if (editId != null && editId > 0) {
                                            navController.navigate("create_sale?editId=$editId")
                                        } else {
                                            navController.navigate("create_sale")
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "create_sale?editId={editId}",
                                arguments = listOf(
                                    navArgument("editId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val editId = backStackEntry.arguments?.getString("editId")?.toLongOrNull()
                                CreateInvoiceScreen(
                                    viewModel = viewModel,
                                    editingInvoiceId = editId,
                                    onNavigateBack = { navController.popBackStack() },
                                    onInvoiceCreated = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("create_sale") {
                                CreateInvoiceScreen(
                                    viewModel = viewModel,
                                    editingInvoiceId = null,
                                    onNavigateBack = { navController.popBackStack() },
                                    onInvoiceCreated = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("products") {
                                ProductsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("customers") {
                                CustomersScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("purchases") {
                                PurchasesScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("suppliers") {
                                SuppliersScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("expenses") {
                                ExpensesScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("reports") {
                                ReportsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }

                        // Floating Post-Save Share Overlay (centered in the screen as requested)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 90.dp),
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
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        launcher.launch(permissionsToRequest.toTypedArray())
    }
}
