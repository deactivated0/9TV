package com.deactivated0.tv9

import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import com.google.gson.Gson
import com.deactivated0.tv9.ISP.CHINA_MOBILE
import com.deactivated0.tv9.ISP.CHINA_TELECOM
import com.deactivated0.tv9.ISP.CHINA_UNICOM
import com.deactivated0.tv9.ISP.UNKNOWN
import com.deactivated0.tv9.requests.HttpClient
import com.deactivated0.tv9.requests.TimeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ISP {
    UNKNOWN,
    CHINA_MOBILE,
    CHINA_UNICOM,
    CHINA_TELECOM;

    fun fromName(name: String): ISP {
        return when (name) {
            "ChinaMobile" -> CHINA_MOBILE
            "ChinaUnicom" -> CHINA_UNICOM
            "ChinaTelecom" -> CHINA_TELECOM
            else -> UNKNOWN
        }
    }
}

data class IpInfo(
    val ip: String,
    val location: Location
)

data class Location(
    val city_name: String,
    val country_name: String,
    val isp_domain: String,
    val latitude: String,
    val longitude: String,
    val owner_domain: String,
    val region_name: String,
)


object Utils {
    @Volatile
    private var between: Long = 0

    val gson: Gson = Gson()

    private val reusableDate = Date()
    private val formatters = object : ThreadLocal<MutableMap<String, SimpleDateFormat>>() {
        override fun initialValue(): MutableMap<String, SimpleDateFormat> = HashMap(4)
    }

    fun getDateFormat(format: String): String {
        val map = formatters.get()!!
        val sdf = map.getOrPut(format) { SimpleDateFormat(format, Locale.CHINA) }
        val now = System.currentTimeMillis() - between
        return synchronized(reusableDate) {
            reusableDate.time = now
            sdf.format(reusableDate)
        }
    }

    fun getDateTimestamp(): Long {
        return (System.currentTimeMillis() - between) / 1000
    }

    suspend fun init() {
        try {
            val currentTimeMillis = getTimestampFromServer()
            if (currentTimeMillis > 0) {
                between = System.currentTimeMillis() - currentTimeMillis
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    init {
        // On-demand clock sync to avoid network contention and socket allocation on app startup
    }

    /**
     * Get timestamp from server
     * @return Long timestamp
     */
    private suspend fun getTimestampFromServer(): Long {
        return withContext(Dispatchers.IO) {
            val client = HttpClient.okHttpClient
            val request = okhttp3.Request.Builder()
                .url("https://api.m.taobao.com/rest/api3.do?api=mtop.common.getTimestamp")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext 0
                    val string = response.body()?.string()
                    gson.fromJson(string, TimeResponse::class.java).data.t.toLong()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }

    suspend fun getISP(): ISP {
        return withContext(Dispatchers.IO) {
            val client = HttpClient.okHttpClient
            val request = okhttp3.Request.Builder()
                .url("https://api.myip.la/json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext UNKNOWN
                    val string = response.body()?.string()
                    val isp = gson.fromJson(string, IpInfo::class.java).location.isp_domain
                    when (isp) {
                        "ChinaMobile" -> CHINA_MOBILE
                        "ChinaUnicom" -> CHINA_UNICOM
                        "ChinaTelecom" -> CHINA_TELECOM
                        else -> UNKNOWN
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                UNKNOWN
            }
        }
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().displayMetrics
        ).toInt()
    }

    fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics
        ).toInt()
    }

    fun isTmallDevice() = Build.MANUFACTURER.equals("Tmall", ignoreCase = true)

    fun formatUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return url
        }
        if (url.startsWith("//")) {
            return "http://$url"
        }
        return "http://$url"
    }

    class TextBadgeDrawable(
        private val text: String,
        textColor: Int,
        textSizePx: Float,
        private val intrinsicWidth: Int = -1,
        private val intrinsicHeight: Int = -1
    ) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }

        override fun draw(canvas: android.graphics.Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, cx, cy, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = intrinsicWidth
        override fun getIntrinsicHeight(): Int = intrinsicHeight
    }
}