package com.example.axesite.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.Serializable
import androidx.compose.material.icons.filled.Edit


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
    val replyCount: Int = 0
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
 * Convenience wrapper for displaying thread info along with latest reply.
 */
data class ThreadWithLastReply(
    val thread: ForumThread,
    val lastReply: Reply?
)

/* -----------------------------------------
 * Firebase Write Functions
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
            "postedTime" to thread.postedTime
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

/* -----------------------------------------
 * AddThreadDialog: For adding a new thread.
 * -----------------------------------------
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddThreadDialog(
    currentUserName: String,
    onDismiss: () -> Unit,
    onSubmit: (ForumThread) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val currentTime = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
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
                Text("Posted by: $currentUserName")
                Text("Time: $currentTime")
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
@OptIn(ExperimentalMaterial3Api::class)
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
    // Retrieve user session info from SharedPreferences
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userNameFromPrefs = sharedPreferences.getString("name", "") ?: ""

    val forumTitle = "axeSite forum"

    // State for threads list
    val threadsState = remember { mutableStateOf<List<ThreadWithLastReply>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Listen to "forums" node in Firebase
    DisposableEffect(Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("forums")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<ThreadWithLastReply>()
                for (forumSnapshot in snapshot.children) {
                    val id = forumSnapshot.key ?: ""
                    val title = forumSnapshot.child("title").getValue(String::class.java) ?: ""
                    val msg = forumSnapshot.child("msg").getValue(String::class.java) ?: ""
                    val postedBy = forumSnapshot.child("postedBy").getValue(String::class.java) ?: ""
                    val postedTime = forumSnapshot.child("postedTime").getValue(String::class.java) ?: ""

                    // Replies
                    val repliesSnapshot = forumSnapshot.child("replies")
                    val replyCount = repliesSnapshot.childrenCount.toInt()

                    // Determine the latest reply (if any)
                    var latestReply: Reply? = null
                    for (child in repliesSnapshot.children) {
                        val replyBy = child.child("replyBy").getValue(String::class.java) ?: ""
                        val replyContent = child.child("replyContent").getValue(String::class.java) ?: ""
                        val replyTime = child.child("replyTime").getValue(String::class.java) ?: ""
                        // Choose the reply with the greatest timestamp value
                        if (latestReply == null ||
                            (replyTime.toLongOrNull() ?: 0) > (latestReply.replyTime.toLongOrNull() ?: 0)
                        ) {
                            latestReply = Reply(replyBy = replyBy, replyContent = replyContent, replyTime = replyTime)
                        }
                    }

                    val forumThread = ForumThread(
                        id = id,
                        title = title,
                        msg = msg,
                        postedBy = postedBy,
                        postedTime = postedTime,
                        replyCount = replyCount
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
            Divider()
            LazyColumn {
                items(threadsState.value) { item ->
                    ForumThreadItem(item, onItemClick = { thread ->
                        navController.navigate("threadDetail/${thread.id}")
                    })
                    Divider()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(threadId: String) {
    // State for the thread details
    val threadState = remember { mutableStateOf<ForumThread?>(null) }
    val loadingThread = remember { mutableStateOf(true) }
    // State for replies list
    val repliesState = remember { mutableStateOf<List<Reply>>(emptyList()) }
    var showAddReplyDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Get current user name from SharedPreferences
    val context = LocalContext.current
    val sp = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val currentUserName = sp.getString("name", "") ?: ""

    // Fetch thread details from Firebase
    LaunchedEffect(threadId) {
        val dbRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId)
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                threadState.value = snapshot.getValue(ForumThread::class.java)
                loadingThread.value = false
            }
            override fun onCancelled(error: DatabaseError) {
                loadingThread.value = false
            }
        })
    }

    // Listen to replies under this thread
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
                    // Show edit button if current user is the thread owner
                    if (threadState.value?.postedBy == currentUserName) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
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
                    Text("Message: ${thread.msg}", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Posted by: ${thread.postedBy}", fontSize = 14.sp)
                    Text("Posted time: ${thread.postedTime}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Replies:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    LazyColumn {
                        items(repliesState.value) { reply ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("Reply by: ${reply.replyBy}", fontWeight = FontWeight.Bold)
                                Text(reply.replyContent)
                                Text("At: ${reply.replyTime}", fontSize = 12.sp)
                            }
                            Divider()
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
                // Update local state with new values
                threadState.value = threadState.value?.copy(
                    title = updatedThread.title,
                    msg = updatedThread.msg
                )
                showEditDialog = false
            }
        )
    }
}

/**
 * EditThreadDialog: Allows the owner to edit thread title and message.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

/**
 * Updates a thread's title and message in Firebase.
 */
fun updateThreadInFirebase(threadId: String, thread: ForumThread) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums").child(threadId)
    dbRef.updateChildren(
        mapOf(
            "title" to thread.title,
            "msg" to thread.msg
        )
    )
}
