@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import android.content.Context
import com.google.firebase.database.*

@Composable
fun TeacherGroupManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val teacherId = sharedPreferences.getString("userId", null) ?: ""
    val userRole = sharedPreferences.getString("role", null) ?: ""

    LaunchedEffect(teacherId, userRole) {
        if (teacherId.isBlank() || userRole != "teacher") {
            navController.navigate("sign_in")
            return@LaunchedEffect
        }
    }

    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
    val modulesRef = database.getReference("modules")
    val groupsRef = database.getReference("groups")
    val enrollmentsRef = database.getReference("enrollments")
    val usersRef = database.getReference("users")

    var modules by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var selectedModule by remember { mutableStateOf<Pair<String, String>?>(null) }
    var groups by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var students by remember { mutableStateOf(listOf<Pair<String, String>>()) }

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
                groupsRef.child(module.first).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        groups = snapshot.children.mapNotNull {
                            val id = it.key ?: return@mapNotNull null
                            val name = it.child("groupName").getValue(String::class.java) ?: "Unnamed"
                            id to name
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

                enrollmentsRef.get().addOnSuccessListener { snapshot ->
                    val enrolledStudentIds = snapshot.children.filter { it.child(module.first).exists() }.mapNotNull { it.key }
                    usersRef.get().addOnSuccessListener { usersSnapshot ->
                        students = usersSnapshot.children.filter { it.key in enrolledStudentIds }.mapNotNull {
                            val id = it.key ?: return@mapNotNull null
                            val name = it.child("name").getValue(String::class.java) ?: "Unnamed"
                            id to name
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Add Group", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = {
                    selectedModule?.let { module ->
                        groupsRef.child(module.first).get().addOnSuccessListener { snapshot ->
                            val nextGroupNumber = snapshot.children.count() + 1
                            val newGroupRef = groupsRef.child(module.first).push()
                            newGroupRef.setValue(
                                mapOf("groupName" to "Group $nextGroupNumber", "createdBy" to teacherId)
                            ).addOnSuccessListener {
                                groups = groups + (newGroupRef.key!! to "Group $nextGroupNumber")
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Group")
                }
            }

            Spacer(Modifier.height(16.dp))

            groups.forEachIndexed { index, group ->
                var expanded by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(group.second, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Default.Add, contentDescription = "Manage Members")
                            }
                            if (index == groups.size - 1) {
                                IconButton(onClick = {
                                    groupsRef.child(selectedModule!!.first).child(group.first).removeValue()
                                    groups = groups.dropLast(1)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Group")
                                }
                            }
                        }

                        if (expanded) {
                            students.forEach { student ->
                                var isChecked by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    groupsRef.child(selectedModule!!.first).child(group.first).child("members").child(student.first).get()
                                        .addOnSuccessListener { snapshot ->
                                            isChecked = snapshot.exists()
                                        }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            isChecked = checked
                                            val studentRef = groupsRef.child(selectedModule!!.first).child(group.first).child("members").child(student.first)
                                            if (checked) studentRef.setValue(true) else studentRef.removeValue()
                                        }
                                    )
                                    Text(student.second)
                                }
                            }
                        }
                    }
                }
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
    var showDialog by remember { mutableStateOf(false) }
    var groupMembers by remember { mutableStateOf(listOf<String>()) }
    var studentGroups by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(studentId, userRole) {
        if (studentId.isBlank() || userRole.isBlank()) {
            navController.navigate("sign_in")
            return@LaunchedEffect
        }

        if (userRole != "student") {
            navController.navigate("teacher_group")
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

        groupsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                studentGroups = snapshot.children.flatMap { module ->
                    module.children.filter { it.child("members").hasChild(studentId) }
                        .mapNotNull { it.key }
                }.toSet()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Join Groups") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            moduleGroups.forEach { (moduleId, groups) ->
                Text("Module: $moduleId", style = MaterialTheme.typography.headlineSmall)
                groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(group.second, modifier = Modifier.weight(1f))
                        if (studentGroups.contains(group.first)) {
                            Button(onClick = {
                                groupsRef.child(moduleId).child(group.first).child("members").child(studentId).removeValue()
                                studentGroups = studentGroups - group.first
                            }) {
                                Text("Leave")
                            }
                        } else {
                            Button(onClick = {
                                val groupRef = groupsRef.child(moduleId).child(group.first)
                                groupRef.child("members").child(studentId).setValue(true)
                                studentGroups = studentGroups + group.first
                            }) {
                                Text("Join")
                            }
                        }
                    }

                    TextButton(onClick = {
                        val groupRef = groupsRef.child(moduleId).child(group.first).child("members")
                        groupRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val userIds = snapshot.children.mapNotNull { it.key }
                                val usersRef = database.getReference("users")

                                val fetchedNames = mutableListOf<String>()

                                userIds.forEach { userId ->
                                    usersRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(userSnapshot: DataSnapshot) {
                                            val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                                            fetchedNames.add(userName)

                                            // Update state when all names are fetched
                                            if (fetchedNames.size == userIds.size) {
                                                groupMembers = fetchedNames
                                                showDialog = true
                                            }
                                        }

                                        override fun onCancelled(error: DatabaseError) {}
                                    })
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }) {
                        Text("View Members")
                    }

                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Group Members") },
            text = {
                Column {
                    if (groupMembers.isEmpty()) {
                        Text("No members in this group yet.")
                    } else {
                        groupMembers.forEach { member ->
                            Text("• $member")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
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
