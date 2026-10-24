package com.deactivated0.tv9

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.Locale

class MyTVApplication : MultiDexApplication() {

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
        private const val TAG = "MyTVApplication"
        private lateinit var instance: MyTVApplication

        @JvmStatic
        fun getInstance(): MyTVApplication {
            return instance
        }
    }

    private lateinit var displayMetrics: DisplayMetrics
    private lateinit var realDisplayMetrics: DisplayMetrics
    private val mainHandler = Handler(Looper.getMainLooper())

    private var width = 0
    private var height = 0
    private var shouldWidth = 0
    private var shouldHeight = 0
    private var ratio = 1.0
    private var density = 2.0f
    private var scale = 1.0f

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize persistent disk logger and uncaught crash handler
        AppLogger.init(this)

        // 2. Initialize Conscrypt security provider for modern TLS 1.2/1.3
        try {
            val conscrypt = Conscrypt.newProvider()
            Security.insertProviderAt(conscrypt, 1)
            AppLogger.i(TAG, "Conscrypt security provider installed successfully")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to install Conscrypt provider", t)
        }

        // 3. Initialize SSL certificates and host verification
        IgnoreSSLCertificate.ignore()

        displayMetrics = DisplayMetrics()
        realDisplayMetrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val defaultDisplay = windowManager.defaultDisplay
        defaultDisplay.getMetrics(displayMetrics)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            defaultDisplay.getRealMetrics(realDisplayMetrics)
        } else {
            defaultDisplay.getMetrics(realDisplayMetrics)
        }

        if (realDisplayMetrics.heightPixels > realDisplayMetrics.widthPixels) {
            width = realDisplayMetrics.heightPixels
            height = realDisplayMetrics.widthPixels
        } else {
            width = realDisplayMetrics.widthPixels
            height = realDisplayMetrics.heightPixels
        }

        density = Resources.getSystem().displayMetrics.density
        scale = displayMetrics.scaledDensity

        if ((width.toDouble() / height) < (16.0 / 9.0)) {
            ratio = width * 2 / 1920.0 / density
            shouldWidth = width
            shouldHeight = (width * 9.0 / 16.0).toInt()
        } else {
            ratio = height * 2 / 1080.0 / density
            shouldHeight = height
            shouldWidth = (height * 16.0 / 9.0).toInt()
        }

        Thread.setDefaultUncaughtExceptionHandler(MyTVExceptionHandler(this))
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppLogger.w(TAG, "onTrimMemory level=$level")
        try {
            com.bumptech.glide.Glide.get(this).onTrimMemory(level)
        } catch (ignored: Throwable) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLogger.w(TAG, "onLowMemory triggered")
        try {
            com.bumptech.glide.Glide.get(this).onLowMemory()
        } catch (ignored: Throwable) {}
    }

    fun getDisplayMetrics(): DisplayMetrics {
        return displayMetrics
    }

    fun toast(message: CharSequence = "", duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(applicationContext, message, duration).show()
        } else {
            mainHandler.post {
                Toast.makeText(applicationContext, message, duration).show()
            }
        }
    }

    fun shouldWidthPx(): Int {
        return shouldWidth
    }

    fun shouldHeightPx(): Int {
        return shouldHeight
    }

    fun dp2Px(dp: Int): Int {
        return (dp * ratio * density + 0.5f).toInt()
    }

    fun px2Px(px: Int): Int {
        return (px * ratio + 0.5f).toInt()
    }

    fun px2PxFont(px: Float): Float {
        return (px * ratio / scale).toFloat()
    }

    fun sp2Px(sp: Float): Float {
        return (sp * ratio * scale).toFloat()
    }

    override fun attachBaseContext(base: Context) {
        val locale = Locale.ENGLISH
        val context = LocaleContextWrapper.wrap(base, locale)
        super.attachBaseContext(context)
    }
}