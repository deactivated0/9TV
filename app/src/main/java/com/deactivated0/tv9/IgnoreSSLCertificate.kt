package com.deactivated0.tv9

import org.conscrypt.Conscrypt
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object IgnoreSSLCertificate {

    @Volatile
    private var initialized = false

    fun ignore() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                try {
                    val conscrypt = Conscrypt.newProvider()
                    Security.insertProviderAt(conscrypt, 1)
                } catch (ignored: Throwable) {}

                val trustAllCerts: Array<TrustManager> = arrayOf(
                    object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate>? {
                            return null
                        }

                        override fun checkClientTrusted(
                            certs: Array<X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            certs: Array<X509Certificate?>?,
                            authType: String?
                        ) {
                        }
                    }
                )

                val sc = try {
                    SSLContext.getInstance("TLS", Conscrypt.newProvider())
                } catch (t: Throwable) {
                    try {
                        SSLContext.getInstance("TLS")
                    } catch (t2: Throwable) {
                        SSLContext.getInstance("SSL")
                    }
                }
                sc.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

                val allHostsValid = HostnameVerifier { _, _ -> true }
                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
                initialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
