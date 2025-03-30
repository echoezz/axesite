package com.example.axesite.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
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
import java.io.File
import java.io.IOException

data class AttendanceRecord(val module: String, val formattedTime: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val TARGET_LATITUDE = 1.4137966258157593
    val TARGET_LONGITUDE = 103.9125546343906
    val ALLOWED_RADIUS_METERS = 500000.0f

    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
    val userId = sharedPreferences.getString("userId", "") ?: ""
    val userNameFromPrefs = sharedPreferences.getString("name", "") ?: ""
    val userRole = sharedPreferences.getString("role", "") ?: ""
    var moduleNames by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var attendanceRecords by remember { mutableStateOf(listOf<AttendanceRecord>()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedModule by remember { mutableStateOf("") }

    var teacherAttendanceRecords by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            errorMessage = "Location permission is required to clock in."
        }
    }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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

    LaunchedEffect(userId) {
        if (userRole != "teacher") {
            val database = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
            val enrollRef = database.getReference("enrollments").child(userId)
            Log.e("userId", userId)
            Log.d("enrollRef", enrollRef.toString())
            enrollRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val moduleIds = snapshot.children.mapNotNull { child ->
                        val isEnrolled = child.getValue(Boolean::class.java) ?: false
                        if (isEnrolled) child.key else null
                    }
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

    fun processLocation(location: Location, module: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "system_cache")

                if (!file.exists()) {
                    file.createNewFile()
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val data = """
                Module: $module
                Latitude: ${location.latitude}
                Longitude: ${location.longitude}
                Timestamp: $timestamp
                ------------------------
            """.trimIndent()

                file.appendText("$data\n\n")
            } catch (_: IOException) {
            }
        }

        val targetLocation = Location("").apply {
            latitude = TARGET_LATITUDE
            longitude = TARGET_LONGITUDE
        }
        val distance = location.distanceTo(targetLocation)
        if (distance <= ALLOWED_RADIUS_METERS) {
            val attendanceRef = FirebaseDatabase.getInstance("https://mobile-sec-b6625-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("attendance")
            val clockInTimestamp = System.currentTimeMillis()
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
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { currentLocation: Location? ->
                if (currentLocation != null) {
                    Log.d("ClockIn", "Using getCurrentLocation.")
                    processLocation(currentLocation, module)
                } else {
                    Log.d("ClockIn", "getCurrentLocation returned null, trying lastLocation.")
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation: Location? ->
                        if (lastLocation != null) {
                            Log.d("ClockIn", "Using last known location.")
                            processLocation(lastLocation, module)
                        } else {
                            Log.e("ClockIn", "Both getCurrentLocation and lastLocation returned null, requesting updates as fallback.")
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
            TopAppBar(title = { Text("Attendance") })
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
                                            fetchTeacherAttendance(selectedModule) { records ->
                                                teacherAttendanceRecords = records
                                            }
                                        } else {
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