package com.example.axesite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.example.axesite.navigation.AuthNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase here
        FirebaseApp.initializeApp(this)
        setContent {
            val navController = rememberNavController()
            AuthNavGraph(navController)
        }
    }
}
