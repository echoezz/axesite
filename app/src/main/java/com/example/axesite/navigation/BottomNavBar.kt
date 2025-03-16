package com.example.axesite.navigation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.axesite.screens.SignInScreen
import com.example.axesite.screens.HomeScreen
import com.example.axesite.screens.ModifyScreen
import com.example.axesite.screens.ProfileScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BottomNavBarApp()
        }
    }
}

@Composable
fun BottomNavBarApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Log.d("BottomNavBar", "Current route (BottomNavBarApp): $currentRoute")

    Scaffold(
        bottomBar = {
            Log.d("BottomNavBar", "Inside bottomBar, currentRoute: $currentRoute")
            if (currentRoute !in listOf("login")) {
                Log.d("BottomNavBar", "Showing BottomNavBar")
                BottomNavBar(navController)
            } else {
                Log.d("BottomNavBar", "Hiding BottomNavBar")
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            composable("login") { SignInScreen(navController) }
            composable("home") { HomeScreen() }
            composable("modify") { ModifyScreen() }
            composable("profile") { ProfileScreen() }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val items = listOf(
        NavItem("Home", "home"),
        NavItem("Modify", "modify"),
        NavItem("Profile", "profile")
    )

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Log.d("BottomNavBar", "Current route (BottomNavBar): $currentRoute")

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    Log.d("BottomNavBar", "Navigating to: ${item.route}")
                    navController.navigate(item.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { /* Add icons if needed */ }
            )
        }
    }
}

data class NavItem(val title: String, val route: String)
