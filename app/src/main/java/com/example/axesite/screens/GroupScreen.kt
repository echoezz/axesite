@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import com.google.firebase.database.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import android.content.Context


// Teacher Group Management Screen
@Composable
fun TeacherGroupManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)

    val teacherId = sharedPreferences.getString("userId", null) ?: ""
    val userRole = sharedPreferences.getString("role", null) ?: ""

    // Validate session
    LaunchedEffect(teacherId, userRole) {
        if (teacherId.isBlank() || userRole.isBlank()) {
            navController.navigate("sign_in")  // Redirect if session is missing
            return@LaunchedEffect
        }

        if (userRole != "teacher") {
            navController.navigate("student_group")  // Redirect students away from teacher screen
            return@LaunchedEffect
        }
    }

    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val modulesRef = database.getReference("modules")
    val groupsRef = database.getReference("groups")

    var modules by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedModule by remember { mutableStateOf<Pair<String, String>?>(null) }
    var groups by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var newGroupName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        modulesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                modules = snapshot.children.mapNotNull {
                    val id = it.key ?: return@mapNotNull null
                    val name = it.child("moduleName").getValue(String::class.java) ?: "Unnamed"
                    id to name
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Manage Groups") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            DropdownMenuExample("Select Module", modules, selectedModule) { module ->
                selectedModule = module
                groupsRef.child(module.first)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            groups = snapshot.children.mapNotNull {
                                val id = it.key ?: return@mapNotNull null
                                val name = it.child("groupName").getValue(String::class.java) ?: "Unnamed"
                                id to name
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = { Text("New Group Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = {
                selectedModule?.let { module ->
                    val newGroupRef = groupsRef.child(module.first).push()
                    newGroupRef.setValue(
                        mapOf("groupName" to newGroupName, "createdBy" to teacherId)
                    )
                    newGroupName = ""
                }
            }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Create Group")
            }

            groups.forEach { group ->
                Text(group.second, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// Student Group Viewing/Joining Screen
@Composable
fun StudentGroupJoinScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)

    val studentId = sharedPreferences.getString("userId", null) ?: ""
    val userRole = sharedPreferences.getString("role", null) ?: ""

    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val enrollmentsRef = database.getReference("enrollments").child(studentId)
    val groupsRef = database.getReference("groups")

    var enrolledModules by remember { mutableStateOf(setOf<String>()) }
    var moduleGroups by remember { mutableStateOf(mapOf<String, List<Pair<String, String>>>()) }

    LaunchedEffect(studentId, userRole) {
        if (studentId.isBlank() || userRole.isBlank()) {
            navController.navigate("sign_in")  // Redirect if session is missing
            return@LaunchedEffect
        }

        if (userRole != "student") {
            navController.navigate("home")  // Redirect if user is NOT a student
            return@LaunchedEffect
        }

        enrollmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                enrolledModules = snapshot.children.mapNotNull { it.key }.toSet()
                enrolledModules.forEach { moduleId ->
                    groupsRef.child(moduleId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            moduleGroups = moduleGroups + (moduleId to snapshot.children.mapNotNull {
                                val id = it.key ?: return@mapNotNull null
                                val name = it.child("groupName").getValue(String::class.java) ?: "Unnamed"
                                id to name
                            })
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Join Groups") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            moduleGroups.forEach { (moduleId, groups) ->
                Text("Module: $moduleId", style = MaterialTheme.typography.headlineSmall)
                groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.second, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            groupsRef.child(moduleId).child(group.first).child("members").child(studentId).setValue(true)
                        }) {
                            Text("Join")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}


@Composable
fun DropdownMenuExample(
    placeholder: String,
    items: List<Pair<String, String>>,
    selectedItem: Pair<String, String>?,
    onItemSelected: (Pair<String, String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedItem?.second ?: placeholder)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.second) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}
