@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var profilePicUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    // Launcher to pick a new profile picture from the gallery.
    val galleryLauncher = rememberLauncherForActivityResult(contract = GetContent()) { uri: Uri? ->
        uri?.let {
            // Upload the image and update profilePicUrl in database.
            uploadProfileImage(it, userId) { downloadUrl ->
                profilePicUrl = downloadUrl
            }
        }
    }

    LaunchedEffect(userId) {
        if (userId.isBlank()) {
            navController.navigate("signin")
            return@LaunchedEffect
        }
        val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
        val userRef = database.getReference("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullName = snapshot.child("name").getValue(String::class.java) ?: "N/A"
                email = snapshot.child("email").getValue(String::class.java) ?: "N/A"
                role = snapshot.child("role").getValue(String::class.java) ?: "user"
                profilePicUrl = snapshot.child("profilePic").getValue(String::class.java) ?: ""
                loading = false
            }
            override fun onCancelled(error: DatabaseError) {
                loading = false
            }
        })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The profile picture is clickable.
                    if (profilePicUrl.isNotEmpty()) {
                        AsyncImage(
                            model = profilePicUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .clickable { galleryLauncher.launch("image/*") }
                        )
                    } else {
                        // Placeholder: show first letter of the user's name.
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fullName.firstOrNull()?.toString() ?: "U",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Name: $fullName", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Role: $role", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Uploads a new profile image to Firebase Storage and updates the user's profilePic in Realtime Database.
 *
 * @param uri The URI of the selected image.
 * @param userId The user's ID.
 * @param onSuccess Callback with the download URL.
 */
fun uploadProfileImage(uri: Uri, userId: String, onSuccess: (String) -> Unit) {
    // Create a unique filename.
    val filename = "${System.currentTimeMillis()}.jpg"
    val storage = FirebaseStorage.getInstance("gs://mobile-sec-b6625.firebasestorage.app")
    val storageRef = storage.reference.child("profile/$filename")

    storageRef.putFile(uri)
        .addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val downloadUrl = downloadUri.toString()
                // Now update the user's profilePic field in the Realtime Database.
                val userRef = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users").child(userId)
                userRef.updateChildren(mapOf("profilePic" to downloadUrl))
                    .addOnSuccessListener { onSuccess(downloadUrl) }
                    .addOnFailureListener { exception ->
                        Log.e("Profile", "Failed to update profilePic: ${exception.message}")
                    }
            }.addOnFailureListener { exception ->
                Log.e("Profile", "Failed to get download URL: ${exception.message}")
            }
        }
        .addOnFailureListener { exception ->
            Log.e("Profile", "Image upload failed: ${exception.message}")
        }
}
