package com.example.healthup.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.healthup.screens.SignInScreen
import com.example.healthup.screens.SignUpScreen

@Composable
fun AuthNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "signin") {
        composable("signin") { SignInScreen(navController) }
        composable("signup") { SignUpScreen(navController) }
    }
}
