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
import com.example.axesite.screens.EnrollmentScreen
import com.example.axesite.screens.StudentGroupJoinScreen
import com.example.axesite.screens.TeacherGroupManagementScreen
import com.example.axesite.screens.ThreadDetailScreen

@Composable
fun AuthNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "signin") {
        composable("signin") { SignInScreen(navController) }
        composable("signup") { SignUpScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("threadDetail/{threadId}") { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
            ThreadDetailScreen(navController=navController,threadId = threadId)
        }
        composable("profile") { ProfileScreen(navController) }
        composable("forum") { ForumsScreen(navController) } // edited here
        composable("enroll") { EnrollmentScreen(navController)}
        composable("teacher_group") { TeacherGroupManagementScreen(navController) }
        composable("student_group") { StudentGroupJoinScreen(navController) }

    }
}
