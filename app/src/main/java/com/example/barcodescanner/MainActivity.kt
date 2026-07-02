package com.example.barcodescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.barcodescanner.ui.screen.CameraScreen
import com.example.barcodescanner.ui.screen.HomeScreen
import com.example.barcodescanner.ui.screen.ImageScanScreen
import com.example.barcodescanner.ui.screen.ResultScreen
import com.example.barcodescanner.ui.theme.BarcodeScannerTheme
import com.example.barcodescanner.ui.viewmodel.ScanViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodeScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BarcodeScannerApp()
                }
            }
        }
    }
}

@Composable
fun BarcodeScannerApp() {
    val navController = rememberNavController()
    val viewModel: ScanViewModel = viewModel()

    var currentResult by remember { mutableStateOf(ResultData()) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCameraClick = { navController.navigate(Screen.Camera.route) },
                onImageClick = { navController.navigate(Screen.ImageScan.route) }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onResultFound = { text, format, type ->
                    currentResult = ResultData(text, format, type)
                    navController.navigate(Screen.Result.route)
                }
            )
        }

        composable(Screen.ImageScan.route) {
            ImageScanScreen(
                onBackClick = { navController.popBackStack() },
                onResultFound = { text, format, type ->
                    currentResult = ResultData(text, format, type)
                    navController.navigate(Screen.Result.route)
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                rawValue = currentResult.value,
                format = currentResult.format,
                valueType = currentResult.type,
                onCopyClick = { },
                onShareClick = { },
                onHomeClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object ImageScan : Screen("image_scan")
    object Result : Screen("result")
}

data class ResultData(
    val value: String = "",
    val format: String = "",
    val type: String = ""
)
