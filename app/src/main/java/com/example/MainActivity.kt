package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.autoresize.AutoResizeProvider
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DataloggerScreen
import com.example.ui.screens.DiagnosticScreen
import com.example.ui.screens.AiDiagnosticScreen
import com.example.ui.theme.AstraCyan
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.GmAmber
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.AppScreen
import com.example.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AutoResizeProvider(viewModel = viewModel) {
                    MainContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainContent(viewModel: ScannerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    // Solicitação de permissões de Bluetooth e Notificação
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.loadPairedDevices()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Regra obrigatória: BackHandler para retornar à tela inicial (Conexão ou Dashboard)
    BackHandler(enabled = currentScreen != AppScreen.CONNECTION) {
        if (currentScreen != AppScreen.DASHBOARD) {
            viewModel.setScreen(AppScreen.DASHBOARD)
        } else {
            viewModel.setScreen(AppScreen.CONNECTION)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = CarbonCard,
                contentColor = GmAmber,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CONNECTION,
                    onClick = { viewModel.setScreen(AppScreen.CONNECTION) },
                    icon = {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = "Conectar"
                        )
                    },
                    label = { Text("Conexão", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = GmAmber,
                        indicatorColor = GmAmber,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_connection")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = { viewModel.setScreen(AppScreen.DASHBOARD) },
                    icon = {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = "Cockpit"
                        )
                    },
                    label = { Text("Cockpit", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = GmAmber,
                        indicatorColor = GmAmber,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_dashboard")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.AI_DIAGNOSTIC,
                    onClick = { viewModel.setScreen(AppScreen.AI_DIAGNOSTIC) },
                    icon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "IA Diagnóstico"
                        )
                    },
                    label = { Text("IA Scan", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AstraCyan,
                        indicatorColor = AstraCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_ai_diagnostic")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.DIAGNOSTICS,
                    onClick = { viewModel.setScreen(AppScreen.DIAGNOSTICS) },
                    icon = {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = "Diagnóstico"
                        )
                    },
                    label = { Text("DTC", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = GmAmber,
                        indicatorColor = GmAmber,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_diagnostics")
                )

                NavigationBarItem(
                    selected = currentScreen == AppScreen.DATALOGGER,
                    onClick = { viewModel.setScreen(AppScreen.DATALOGGER) },
                    icon = {
                        Icon(
                            Icons.Default.ListAlt,
                            contentDescription = "Datalogger"
                        )
                    },
                    label = { Text("Datalog", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = GmAmber,
                        indicatorColor = GmAmber,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_datalogger")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CarbonDark)
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.CONNECTION -> ConnectionScreen(
                    viewModel = viewModel,
                    onNavigateToDashboard = { viewModel.setScreen(AppScreen.DASHBOARD) }
                )
                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                AppScreen.AI_DIAGNOSTIC -> AiDiagnosticScreen(viewModel = viewModel)
                AppScreen.DIAGNOSTICS -> DiagnosticScreen(viewModel = viewModel)
                AppScreen.DATALOGGER -> DataloggerScreen(viewModel = viewModel)
            }
        }
    }
}
