package com.example.isla

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

class IslandService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var lp: WindowManager.LayoutParams
    private var web: WebView? = null
    private var mode = "compact"          // compact | exp | tall
    private var posX = 50.0               // % horizontal
    private var posY = 10.0               // desplazamiento vertical
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        showIsland()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("isla", "Isla dinámica", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, "isla")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Isla dinámica activa")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun topInset(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            )
            if (i.top > 0) return i.top
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun applyLayout() {
        val sw = resources.displayMetrics.widthPixels
        val ew = minOf(dp(340), sw - dp(16))
        when (mode) {
            "tall" -> { lp.width = ew; lp.height = dp(300) }
            "exp" -> { lp.width = ew; lp.height = dp(130) }
            else -> { lp.width = dp(160); lp.height = dp(38) }
        }
        lp.x = ((posX - 50.0) / 100.0 * sw).toInt()
        lp.y = maxOf(dp(4), (topInset() - dp(34)) / 2) + dp((posY - 10.0).toInt())

        web?.let {
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            it.visibility = if (landscape) View.GONE else View.VISIBLE
            if (it.isAttachedToWindow) wm.updateViewLayout(it, lp)
        }
    }

    private fun showIsland() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        lp = WindowManager.LayoutParams(
            dp(160), dp(38),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }

        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/island.html")
        }
        applyLayout()
        wm.addView(web, lp)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ui.post { applyLayout() }
    }

    /** Puente JS → Kotlin. Llamadas, música, notificaciones y voz se agregan después. */
    inner class Bridge {
        @JavascriptInterface fun setMode(m: String) { ui.post { mode = m; applyLayout() } }
        @JavascriptInterface fun setPos(x: Double, y: Double) { ui.post { posX = x; posY = y; applyLayout() } }
        @JavascriptInterface fun close() { ui.post { stopSelf() } }
    }

    override fun onDestroy() {
        web?.let { wm.removeView(it); it.destroy() }
        super.onDestroy()
    }
}
