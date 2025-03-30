package x.c.d.q.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AnalyticsService : Service() {
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}