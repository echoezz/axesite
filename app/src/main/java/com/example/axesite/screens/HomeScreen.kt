@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(navController: NavHostController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""
    val username = sharedPreferences.getString("name", "User") ?: "User"
    val userRole = sharedPreferences.getString("role", "student") ?: "student"

    // State for modules
    var moduleNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var displayedText by remember { mutableStateOf("") }




    // Fetch user’s enrollments on first composition (for students)
    LaunchedEffect(userId) {
        if (userId.isBlank()) {
            navController.navigate("signin")
            return@LaunchedEffect
        }
        // Only fetch enrolled modules for students
        if (userRole != "teacher") {
            val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val enrollRef = database.getReference("enrollments").child(userId)
            enrollRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Each child key represents a module ID if the user is enrolled (value true)
                    val moduleIds = snapshot.children.mapNotNull { child ->
                        val isEnrolled = child.getValue(Boolean::class.java) ?: false
                        if (isEnrolled) child.key else null
                    }
                    // For each module ID, fetch the module name from "modules/<moduleId>"
                    fetchModuleNames(database, moduleIds) { names ->
                        moduleNames = names
                        loading = false
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    loading = false
                }
            })
        } else {
            // For teachers, skip module fetch since they don't have enrollments
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("axeSite Home") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = "Welcome, $username!",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
//                    Button(
//                        onClick = { navController.navigate("forum") },
//                        modifier = Modifier.fillMaxWidth(0.8f)
//                    ) {
//                        Text("Go to Forum")
//                    }
//                    Spacer(modifier = Modifier.height(8.dp))
//                    Button(
//                        onClick = { navController.navigate("profile") },
//                        modifier = Modifier.fillMaxWidth(0.8f)
//                    ) {
//                        Text("Go to Profile")
//                    }
                    // Teachers see an extra Enrollment button
                    if (userRole == "teacher") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { navController.navigate("enroll") },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Go to Enrollment")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Only display enrolled modules for students
                    if (userRole != "teacher") {
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
                    // Dropdown menu below the Enrollment button
                    var expanded by remember { mutableStateOf(false) }
                    var selectedOption by remember { mutableStateOf("Select Option") }
                    val options by remember { mutableStateOf(moduleNames) }


                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedOption,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(),
                            label = { Text("Choose the course ID to view course") },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown"
                                    )
                                }
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedOption = option
                                        expanded = false
                                        displayedText = "You selected: " // Update text below
                                        // Handle selection logic if needed
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = displayedText, style = MaterialTheme.typography.bodyMedium)
                    ModuleScreen(selectedOption, navController);
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
                // Assuming the module node contains a field "moduleName"
                val moduleName =
                    snapshot.child("moduleName").getValue(String::class.java) ?: moduleId
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



@Composable
fun ModuleScreen(module: String, navController: NavHostController) {
    val modulePosts = remember { mutableStateListOf<Pair<String, String>>() }
    val database =
        FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val enrollRef = database.getReference("moduleDescription").child(module)
    enrollRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists()) {
                modulePosts.clear()
                for (weekSnapshot in snapshot.children) {
                    val weekNumber = weekSnapshot.key ?: "Unknown Week"
                    val description = weekSnapshot.child("description").getValue(String::class.java)
                    if (description != null) {
                        modulePosts.add(weekNumber to description)
                    }
                }
                Log.d("FirebaseData", "Updated modulePosts: $modulePosts")
            } else {
                Log.d("FirebaseData", "No data found for $module")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("FirebaseError", "Error: ${error.message}")
        }
    })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between posts
    ) {
        items(modulePosts) { (week, post) ->
            ModulePostCard(
                week = week,
                postText = post,
                onClick = {
                    navController.navigate("forum_detail/$module/$week") // ✅ Navigate on click
                }
            )
        }
    }
}
@Composable
fun ModulePostCard(week: String, postText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp) // Adds a shadow effect

    ) {
        Column(
            modifier = Modifier
                .padding(16.dp) // Inner padding inside the card
        ) {
            Text(
                text = week.replace("week", "Week "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp)) // Adds space between week and description
            Text(
                text = postText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


