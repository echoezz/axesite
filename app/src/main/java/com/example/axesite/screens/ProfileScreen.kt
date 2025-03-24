@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.compose.AsyncImage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import androidx.core.content.ContextCompat
import android.util.Log

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

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadProfileImage(it, userId) { downloadUrl ->
                profilePicUrl = downloadUrl
            }
        }
    }

    // Permission launcher for Android 10 and below
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to change profile picture",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Permission launcher for Android 11+ (MANAGE_EXTERNAL_STORAGE)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                galleryLauncher.launch("image/*")
            } else {
                Toast.makeText(
                    context,
                    "Storage permission is required to change profile picture",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Function to handle image selection with proper permission checks
    fun handleImageSelection() {
        when {
            // Android 11+ - Use MANAGE_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    galleryLauncher.launch("image/*")
                } else {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${context.packageName}")
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                }
            }
            // Android 10 - Use READ_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    galleryLauncher.launch("image/*")
                } else {
                    legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            // Android 9 and below - Use READ_EXTERNAL_STORAGE
            else -> {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    galleryLauncher.launch("image/*")
                } else {
                    legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    // Load user data
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
                    if (profilePicUrl.isNotEmpty()) {
                        AsyncImage(
                            model = profilePicUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .clickable { handleImageSelection() }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                .clickable { handleImageSelection() },
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
                }
            }
        }
    }
}

/**
 * Uploads profile image to Firebase Storage
 */
fun uploadProfileImage(uri: Uri, userId: String, onSuccess: (String) -> Unit) {
    val filename = "${System.currentTimeMillis()}.jpg"
    val storage = FirebaseStorage.getInstance("gs://mobile-sec-b6625.firebasestorage.app")
    val storageRef = storage.reference.child("profile/$filename")

    storageRef.putFile(uri)
        .addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val downloadUrl = downloadUri.toString()
                FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users").child(userId)
                    .updateChildren(mapOf("profilePic" to downloadUrl))
                    .addOnSuccessListener { onSuccess(downloadUrl) }
                    .addOnFailureListener { e ->
                        Log.e("Profile", "Update failed: ${e.message}")
                    }
            }.addOnFailureListener { e ->
                Log.e("Profile", "Download URL failed: ${e.message}")
            }
        }
        .addOnFailureListener { e ->
            Log.e("Profile", "Upload failed: ${e.message}")
        }
}