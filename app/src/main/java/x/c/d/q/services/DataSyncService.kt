package x.c.d.q.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import x.c.d.q.SecurityProvider
import java.util.Timer
import java.util.TimerTask

class DataSyncService : Service() {
    private var timer: Timer? = null
    
    override fun onCreate() {
        super.onCreate()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                SecurityProvider.verify()
            }
        }, 0, 15000)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}