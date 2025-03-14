package com.example.axesite.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*
import com.example.axesite.util.hashPassword


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavController) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    // Role selection state; default is "student"
    var selectedRole by remember { mutableStateOf("student") }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sign Up") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
            Text("Select Role")
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedRole == "teacher",
                    onClick = { selectedRole = "teacher" }
                )
                Text("Teacher")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = selectedRole == "student",
                    onClick = { selectedRole = "student" }
                )
                Text("Student")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    errorMessage = ""
                    val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    val registryRef = database.getReference("users")
                    // Generate a unique user ID
                    val userID = registryRef.push().key ?: run {
                        errorMessage = "Error generating user ID."
                        return@Button
                    }
                    // Check if the generated userID already exists (unlikely with push keys)
                    registryRef.child(userID).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                errorMessage = "User ID already exists."
                            } else {
                                val userMap = mapOf(
                                    "id" to userID,
                                    "name" to fullName,
                                    "email" to email,
                                    "password" to hashPassword(password),
                                    "role" to selectedRole
                                )
                                registryRef.child(userID).setValue(userMap)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Save session data in SharedPreferences
                                            val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                                            sharedPreferences.edit().apply {
                                                putString("userId", userID)
                                                putString("role", selectedRole)
                                                putBoolean("isLoggedIn", true)
                                                apply()
                                            }
                                            // Navigate based on the selected role
                                            if (selectedRole == "teacher") {
                                                navController.navigate("home")
                                            } else {
                                                navController.navigate("home")
                                            }
                                        } else {
                                            errorMessage = "Registration Failed: ${task.exception?.message}"
                                        }
                                    }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            errorMessage = "Error: ${error.message}"
                        }
                    })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Up")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = { navController.navigate("signin") }) {
                Text("Already have an account? Sign In")
            }
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
