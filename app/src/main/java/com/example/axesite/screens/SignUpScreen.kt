package com.example.axesite.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(navController: NavController) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Role is hard-coded for now. You can expand this to a selectable option.
    val role = "user"

    // This state will display error messages, if any.
    var errorMessage by remember { mutableStateOf("") }

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
            Button(
                onClick = {
                    // Clear previous error (if any)
                    errorMessage = ""

                    // Instantiate Firebase connection with your URL
                    val database = FirebaseDatabase.getInstance(
                        "https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/"
                    )
                    // Use the "accounts" node (or "users"—choose one consistently)
                    val registryRef = database.getReference("users")
                    // Generate a unique user ID. (Alternatively, you might use a sanitized email.)
                    val userID = registryRef.push().key ?: run {
                        errorMessage = "Error generating user ID."
                        return@Button
                    }

                    // Check if the user ID already exists
                    registryRef.child(userID).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // Unlikely to happen with push() keys
                                errorMessage = "User ID already exists."
                            } else {
                                // Prepare the user map (hash the password in a real app)
                                val userMap = mapOf(
                                    "id" to userID,
                                    "name" to fullName,
                                    "email" to email,
                                    "password" to hashPassword(password),
                                    "role" to role
                                )
                                // Store user data in the database
                                registryRef.child(userID).setValue(userMap)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Registration succeeded; navigate to home
                                            navController.navigate("home")
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

// Dummy hash function—replace with proper hashing in a production app.
fun hashPassword(password: String): String {
    // For demonstration, this returns the original password.
    // In a real app, hash the password securely.
    return password
}
