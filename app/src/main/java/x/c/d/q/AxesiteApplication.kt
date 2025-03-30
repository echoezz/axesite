package x.c.d.q

import android.app.Application
import android.os.Handler
import android.os.Looper
import x.c.d.q.network.ApiService

class AxesiteApplication : Application() {
    private val apiService = ApiService()
    
    override fun onCreate() {
        super.onCreate()

        SecurityProvider.initialize()
        
        Handler(Looper.getMainLooper()).postDelayed({
            apiService.configure(this)

            Handler(Looper.getMainLooper()).postDelayed({
                // Do nothing
            }, 500)
        }, 100)
    }
}