package com.example.axesite.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.google.firebase.database.*
import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController

@Composable
fun ModifyScreen(navController: NavController) {
    // State to hold the list of student names
    val studentNames = remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedName = remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateOf(false) }

    // Get the current user's name from SharedPreferences
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val currentUserName = sharedPreferences.getString("name", "") ?: ""

    // Fetch student names from Firebase when the screen is composed
    LaunchedEffect(Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("users")
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val names = mutableListOf<String>()
                for (userSnapshot in snapshot.children) {
                    val role = userSnapshot.child("role").getValue(String::class.java)
                    val name = userSnapshot.child("name").getValue(String::class.java)
                    // Add the name to the list only if the role is "student" and the name is not the current user's name
                    if (role == "student" && name != null && name != currentUserName) {
                        names.add(name)
                    }
                }
                studentNames.value = names
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Column to arrange Text and Button vertically
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Chat with lonely students now!",
                style = MaterialTheme.typography.headlineMedium
            )

            // Start Chatting Button
            Box {
                Button(onClick = { expanded.value = true }) {
                    Text(text = "Talk to someone")
                }

                // Show the dropdown menu when the button is clicked
                DropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false }
                ) {
                    studentNames.value.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(text = name) }, // Pass the Text composable as a lambda to the 'text' parameter
                            onClick = {
                                selectedName.value = name
                                expanded.value = false
                            }
                        )
                    }
                }
            }

            // Display the starting message once a name is selected
            selectedName.value?.let { name ->
                Text(
                    text = "Starting chat with $name...",
                    style = MaterialTheme.typography.bodyMedium
                )
                LaunchedEffect(name) {
                    // Fetch the selected user's ID from Firebase
                    val dbRef = FirebaseDatabase.getInstance().getReference("users")
                    dbRef.orderByChild("name").equalTo(name)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                for (userSnapshot in snapshot.children) {
                                    val otherUserId = userSnapshot.key ?: return // Get user ID
                                    val currentUserId = sharedPreferences.getString("userId", "") ?: ""
                                    val chatId = generateChatId(currentUserId, otherUserId)
                                    navController.navigate("chat/$chatId")
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // Handle error
                            }
                        })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewModifyScreen() {
    MaterialTheme {
        // Use rememberNavController() for preview purposes
        val navController = rememberNavController()
        ModifyScreen(navController)
    }
}