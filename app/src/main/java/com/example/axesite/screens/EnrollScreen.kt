@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*
import androidx.core.content.edit // for KTX
import kotlinx.coroutines.delay

@Composable
fun EnrollmentScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""

    // We'll also retrieve the user's name and role if needed
    val username = sharedPreferences.getString("name", "User") ?: "User"
    val userRole = sharedPreferences.getString("role", "student") ?: "student"

    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val studentsRef = database.getReference("users")
    val modulesRef = database.getReference("modules")
    val enrollmentsRef = database.getReference("enrollments")

    var students by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var modules by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedStudent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var enrolledModules by remember { mutableStateOf(setOf<String>()) }
    var tempEnrolledModules by remember { mutableStateOf(setOf<String>()) }

    // Track whether the enrollment operation was successful.
    var operationSuccessful by remember { mutableStateOf(false) }

    // Fetch student users and modules on first composition
    LaunchedEffect(Unit) {
        studentsRef.orderByChild("role").equalTo("student")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    students = snapshot.children.mapNotNull {
                        val id = it.key ?: return@mapNotNull null
                        val name = it.child("name").getValue(String::class.java) ?: "Unnamed"
                        id to name
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        modulesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                modules = snapshot.children.mapNotNull {
                    val id = it.key ?: return@mapNotNull null
                    // Assuming modules node stores an object with a field "moduleName"
                    val name = it.child("moduleName").getValue(String::class.java) ?: "Unnamed"
                    id to name
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Enrollment") }) }) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .padding(16.dp)) {
            if (operationSuccessful) {
                // Show success message and a "Back" button
                Text("Operation successful!", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Back")
                }
            } else {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(selectedStudent?.second ?: "Select Student")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        students.forEach { student ->
                            DropdownMenuItem(
                                text = { Text(student.second) },
                                onClick = {
                                    selectedStudent = student
                                    expanded = false
                                    enrollmentsRef.child(student.first)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                enrolledModules = snapshot.children.mapNotNull { it.key }.toSet()
                                                tempEnrolledModules = enrolledModules
                                            }
                                            override fun onCancelled(error: DatabaseError) {}
                                        })
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                selectedStudent?.let { student ->
                    modules.forEach { module ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = tempEnrolledModules.contains(module.first),
                                onCheckedChange = { enrolled ->
                                    tempEnrolledModules = if (enrolled) tempEnrolledModules + module.first
                                    else tempEnrolledModules - module.first
                                }
                            )
                            Text(module.second)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        enrollmentsRef.child(student.first).setValue(
                            tempEnrolledModules.associateWith { true }
                        )
                        enrolledModules = tempEnrolledModules
                        operationSuccessful = true
                    }) {
                        Text("Confirm Enrollment")
                    }
                }
            }
        }
    }
}
