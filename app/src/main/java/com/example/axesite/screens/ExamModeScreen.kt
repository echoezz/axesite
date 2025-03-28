package com.example.axesite.screens

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.net.Uri
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight

import android.provider.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamModeScreen(navController: NavController) {
    val context = LocalContext.current
    var isExamMode by remember { mutableStateOf(false) }
    val accessibilityStatus = rememberAccessibilityStatus(context)

    // Handle exam mode toggle
    LaunchedEffect(isExamMode) {
        if (isExamMode && !accessibilityStatus) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                context,
                "Please enable Exam Accessibility Service",
                Toast.LENGTH_LONG
            ).show()
            isExamMode = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exam Mode") },
                actions = {
                    Switch(
                        checked = isExamMode,
                        onCheckedChange = { isExamMode = it },
                        enabled = accessibilityStatus || !isExamMode
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (!accessibilityStatus) {
                AlertCard(
                    title = "Accessibility Required",
                    message = "You must enable accessibility services for exam mode",
                    buttonText = "Open Settings",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            } else if (isExamMode) {
                RestrictionInfoCard()
            }
        }
    }
}

@Composable
fun rememberAccessibilityStatus(context: Context): Boolean {
    val enabled = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager
        enabled.value = accessibilityManager.isEnabled
    }

    return enabled.value
}


@Composable
fun AlertCard(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun RestrictionInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Exam Restrictions Active",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            Text(
                text = "• Home button disabled\n" +
                        "• Recent apps blocked\n" +
                        "• App switching prevented",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ADB debugging remains available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}