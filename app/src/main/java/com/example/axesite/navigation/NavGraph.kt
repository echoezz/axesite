package com.example.axesite.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.axesite.screens.ForumsScreen // imported this
import com.example.axesite.screens.SignInScreen
import com.example.axesite.screens.SignUpScreen
import com.example.axesite.screens.HomeScreen
import com.example.axesite.screens.ProfileScreen

@Composable
fun AuthNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "signin") {
        composable("signin") { SignInScreen(navController) }
        composable("signup") { SignUpScreen(navController) }
        composable("home") { HomeScreen() }
        composable("profile") { ProfileScreen(navController) }
        composable("forum") { ForumsScreen(navController) } // edited here
    }
}
