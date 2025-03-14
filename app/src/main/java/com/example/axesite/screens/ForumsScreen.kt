package com.example.axesite.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Data class for your forum items.
 * Added replyTime to capture the time of the last reply.
 */
data class ForumThread(
    val title: String = "",
    val postedBy: String = "",
    val postedTime: String = "",
    val replyBy: String = "",
    val replyContent: String = "",
    val replyTime: String = ""  // New field for reply time
)

/**
 * Writes a new forum thread to Firebase under the "forums" node.
 */
fun addThreadToFirebase(thread: ForumThread) {
    val dbRef = FirebaseDatabase.getInstance().getReference("forums")
    dbRef.push().setValue(thread)
}

/**
 * Composable dialog that lets the user add a new forum thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddThreadDialog(
    onDismiss: () -> Unit,
    onSubmit: (ForumThread) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var postedBy by remember { mutableStateOf("") }
    var postedTime by remember { mutableStateOf("") }
    var replyBy by remember { mutableStateOf("") }
    var replyContent by remember { mutableStateOf("") }
    var replyTime by remember { mutableStateOf("") }

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
                OutlinedTextField(
                    value = postedBy,
                    onValueChange = { postedBy = it },
                    label = { Text("Posted By") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = postedTime,
                    onValueChange = { postedTime = it },
                    label = { Text("Posted Time") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replyBy,
                    onValueChange = { replyBy = it },
                    label = { Text("Reply By") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replyContent,
                    onValueChange = { replyContent = it },
                    label = { Text("Reply Content") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replyTime,
                    onValueChange = { replyTime = it },
                    label = { Text("Reply Time") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(
                    ForumThread(
                        title = title,
                        postedBy = postedBy,
                        postedTime = postedTime,
                        replyBy = replyBy,
                        replyContent = replyContent,
                        replyTime = replyTime
                    )
                )
            }) {
                Text("Submit")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Main composable screen displaying the forum.
 * Data is fetched directly from Firebase without using a ViewModel.
 * The provided navController is available for navigation actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsScreen(navController: NavHostController) {
    val forumTitle = "axeSite forum"
    val threadsState = remember { mutableStateOf<List<ForumThread>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Subscribe to Firebase realtime updates using DisposableEffect.
    DisposableEffect(Unit) {
        val dbRef = FirebaseDatabase.getInstance().getReference("forums")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<ForumThread>()
                for (forumSnapshot in snapshot.children) {
                    val postedBy = forumSnapshot.child("postedBy").getValue(String::class.java) ?: ""
                    val postedTime = forumSnapshot.child("postedTime").getValue(String::class.java) ?: ""
                    val replyBy = forumSnapshot.child("replyBy").getValue(String::class.java) ?: ""
                    val replyContent = forumSnapshot.child("replyContent").getValue(String::class.java) ?: ""
                    val title = forumSnapshot.child("title").getValue(String::class.java) ?: ""
                    val replyTime = forumSnapshot.child("replyTime").getValue(String::class.java) ?: ""
                    newList.add(ForumThread(title, postedBy, postedTime, replyBy, replyContent, replyTime))
                }
                threadsState.value = newList
            }
            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed.
            }
        }
        dbRef.addValueEventListener(listener)
        onDispose { dbRef.removeEventListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = forumTitle) },
                actions = {
                    IconButton(onClick = { /* For example: navController.navigate("search") */ }) {
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
                items(threadsState.value) { thread ->
                    ForumThreadItem(thread = thread)
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddThreadDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { newThread ->
                addThreadToFirebase(newThread)
                showAddDialog = false
            }
        )
    }
}

/**
 * A single item representing one forum thread.
 */
@Composable
fun ForumThreadItem(thread: ForumThread) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row {
            Text(
                text = thread.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "1 Replies", // Replace with an actual reply count if available.
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
        Text(
            text = "Last post by ${thread.replyBy} on ${thread.replyContent} at ${thread.replyTime}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
