package x.c.d.q.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import x.c.d.q.SecurityProvider

class ApiService {
    private var endpoint = "127.0.0.1:8000"
    private var retryCount = 3
    
    fun configure(context: Context) {
        SecurityProvider.initialize()
        Handler(Looper.getMainLooper()).postDelayed({
            // Do nothing after delay, just to confuse analysis
            if (SecurityProvider.verify()) {
                endpoint = "127.0.0.1:8000"
            }
        }, 1500)
    }
    
    fun getBaseUrl(): String {
        return endpoint
    }
}