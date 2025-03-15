package com.example.axesite.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.database.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""
    val username = sharedPreferences.getString("name", "User") ?: "User"

    // State for modules
    var moduleNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // Fetch user’s enrollments on first composition
    LaunchedEffect(userId) {
        if (userId.isBlank()) {
            // If no userId, navigate to sign in or handle as needed
            navController.navigate("signin")
            return@LaunchedEffect
        }

        // 1) Retrieve the module IDs from "enrollments/<userId>"
        val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
        val enrollRef = database.getReference("enrollments").child(userId)

        enrollRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // For each child, if value == true, collect the module ID
                val moduleIds = snapshot.children.mapNotNull { child ->
                    val isEnrolled = child.getValue(Boolean::class.java) ?: false
                    if (isEnrolled) child.key else null
                }
                // 2) For each module ID, fetch the module name from "modules/<moduleId>"
                fetchModuleNames(database, moduleIds) { names ->
                    moduleNames = names
                    loading = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                loading = false
            }
        })
    }

    // UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome, $username!",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { navController.navigate("forum") },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Go to Forum")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("profile") },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Go to Profile")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Modules section
                Text("Enrolled Modules:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (moduleNames.isEmpty()) {
                    Text("Not enrolled in any modules")
                } else {
                    moduleNames.forEach { name ->
                        Text(name)
                    }
                }
            }
        }
    }
}

/**
 * Helper function to fetch each module’s name from "modules/<moduleId>".
 */
private fun fetchModuleNames(
    db: FirebaseDatabase,
    moduleIds: List<String>,
    onComplete: (List<String>) -> Unit
) {
    if (moduleIds.isEmpty()) {
        onComplete(emptyList())
        return
    }

    val modulesRef = db.getReference("modules")
    val moduleNames = mutableListOf<String>()
    var remaining = moduleIds.size

    moduleIds.forEach { moduleId ->
        modulesRef.child(moduleId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If "modules/<moduleId>" is just a string: e.g. "ict2215"
                val moduleName = snapshot.child("moduleName").getValue(String::class.java) ?: moduleId
                moduleNames.add(moduleName)
                remaining--
                if (remaining <= 0) {
                    onComplete(moduleNames)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                remaining--
                if (remaining <= 0) {
                    onComplete(moduleNames)
                }
            }
        })
    }
}
