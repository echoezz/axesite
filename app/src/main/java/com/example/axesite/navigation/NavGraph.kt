package com.example.axesite.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.axesite.screens.ForumsScreen // imported this
import com.example.axesite.screens.SignInScreen
import com.example.axesite.screens.SignUpScreen
import com.example.axesite.screens.HomeScreen
import com.example.axesite.screens.ProfileScreen
import com.example.axesite.screens.EnrollmentScreen
import com.example.axesite.screens.ModuleDetailScreen
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
        composable(
            "module_detail/{module}/{week}",
            arguments = listOf(
                navArgument("module") { type = NavType.StringType },
                navArgument("week") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // Get parameters with EXACT same names as in route
            val module = backStackEntry.arguments?.getString("module") ?: run {
                Log.e("NAV_ERROR", "Missing module parameter")
                ""
            }

            val week = backStackEntry.arguments?.getString("week") ?: run {
                Log.e("NAV_ERROR", "Missing week parameter")
                ""
            }

            ModuleDetailScreen(module = module, week = week)
        }
    }
}


