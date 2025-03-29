package com.example.axesite.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavBackStackEntry
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ForumDetailScreen(backStackEntry: NavBackStackEntry) {
    val week = backStackEntry.arguments?.getString("week") ?: return
    var fileUrl by remember { mutableStateOf<String?>(null) }

    // ✅ Get file URL from Firebase Storage
    LaunchedEffect(week) {
        val storageRef = Firebase.storage.reference
        val docRef = storageRef.child("module/ict2215/$week/test.docx")

        docRef.downloadUrl.addOnSuccessListener { uri ->
            fileUrl = uri.toString()
        }.addOnFailureListener { e ->
            Log.e("Firebase", "Failed to get document URL", e)
        }
    }

    // ✅ Show WebView once the file URL is available
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
