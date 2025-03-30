package com.example.axesite.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavBackStackEntry
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ModuleDetailScreen(module: String, week: String) {
    Log.d("Debug", "ForumDetailScreen is launched!") // ✅ Add this log


    // Check if 'week' argument is missing
    if (week == null) {
        Log.e("Debug", "Week argument is missing") // Log error if 'week' is null
        return // Stop further execution if 'week' is not available
    }

    Log.d("Debug", "Week value received: $week") // ✅ Check week value

    var fileUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }  // Initialize loading state

    val formattedWeek = week.lowercase().replace(" ", "")

    LaunchedEffect(week) {
        val storageRef = Firebase.storage.reference
        val docRef = storageRef.child("module/ict2215/$formattedWeek/test.docx")

        docRef.downloadUrl.addOnSuccessListener { uri ->
            Log.d("Firebase", "File URL retrieved: $uri")
            fileUrl = uri.toString()
            isLoading = false  // Set loading to false once the URL is fetched
        }.addOnFailureListener { e ->
            Log.e("Firebase", "Failed to get document URL", e)
            isLoading = false  // Set loading to false if there's an error
        }
    }

    // Show loading indicator while fetching the URL
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize())
    } else {
        // If fileUrl is fetched, show WebView to display the document
        fileUrl?.let { url ->
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl("https://docs.google.com/gview?embedded=true&url=$url")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
