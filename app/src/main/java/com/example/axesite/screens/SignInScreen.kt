package com.example.axesite.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*
import com.example.axesite.util.hashPassword

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sign In") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = {
                    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    val usersRef = database.getReference("users")
                    // Query users by email
                    val query = usersRef.orderByChild("email").equalTo(email)
                    query.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Loop through matching users (assuming unique emails)
                                for (userSnapshot in snapshot.children) {
                                    val storedPassword = userSnapshot.child("password").getValue(String::class.java)
                                    val userRole = userSnapshot.child("role").getValue(String::class.java) ?: "student"
                                    // Hash the entered password before comparing
                                    if (storedPassword == hashPassword(password)) {
                                        // Save session data in SharedPreferences
                                        val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                                        sharedPreferences.edit().apply {
                                            putString("userId", userSnapshot.key)
                                            putString("role", userRole)
                                            putString("name", userSnapshot.child("name").getValue(String::class.java) ?: "Unknown")
                                            putBoolean("isLoggedIn", true)
                                            apply()
                                        }
                                        // Navigate based on user role
                                        if (userRole == "teacher") {
                                            navController.navigate("teacher_group")
                                        } else {
                                            navController.navigate("student_group")
                                        }
                                    } else {
                                        errorMessage = "Password incorrect"
                                    }
                                }
                            } else {
                                errorMessage = "No user found with that email."
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            errorMessage = "Error: ${error.message}"
                        }
                    })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { navController.navigate("signup") }) {
                Text("Don't have an account? Sign Up")
            }
        }
    }
}
