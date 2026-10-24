package com.deactivated0.tv9.requests

import android.util.Log
import com.deactivated0.tv9.MyTVApplication
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.conscrypt.Conscrypt
import retrofit2.Retrofit
import java.security.Security
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object HttpClient {
    const val TAG = "HttpClient"

    // The branch whose directory holds APK and version.json on the release branch
    const val BUILD_BRANCH = "kk"

    val RELEASE_HOSTS = listOf(
        "https://cdn.jsdelivr.net/gh/deactivated0/9tv@release/",
        "https://raw.githubusercontent.com/deactivated0/9tv/refs/heads/release/",
    )

    val DOWNLOAD_HOSTS = listOf(
        "https://cdn.jsdelivr.net/gh/deactivated0/9tv@release/",
        "https://raw.githubusercontent.com/deactivated0/9tv/refs/heads/release/",
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 4.4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36"

    val okHttpClient: OkHttpClient by lazy {
        getSafeOkHttpClient()
    }

    val configService: ConfigService by lazy {
        Retrofit.Builder()
            .client(okHttpClient)
            .build().create(ConfigService::class.java)
    }

    private fun getSafeOkHttpClient(): OkHttpClient {
        val okHttpBuilder = OkHttpClient.Builder()
            .connectionSpecs(
                Arrays.asList(
                    ConnectionSpec.RESTRICTED_TLS,
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT
                )
            )

        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(requestWithUserAgent)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        try {
            val conscrypt = Conscrypt.newProvider()
            Security.insertProviderAt(conscrypt, 1)
            val tm = InternalX509TrustManager(MyTVApplication.getInstance().applicationContext)
            val sslContext = SSLContext.getInstance("TLS", conscrypt)
            sslContext.init(null, arrayOf(tm), null)
            okHttpBuilder.sslSocketFactory(InternalSSLSocketFactory(sslContext.socketFactory), tm)
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting up Conscrypt TLS, falling back to platform TLS", e)
            try {
                val tm = InternalX509TrustManager(MyTVApplication.getInstance().applicationContext)
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(tm), null)
                okHttpBuilder.sslSocketFactory(InternalSSLSocketFactory(sslContext.socketFactory), tm)
            } catch (e2: Exception) {
                Log.e(TAG, "Error setting up fallback TLS", e2)
            }
        }

        return okHttpBuilder.dns(DnsCache()).retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}
