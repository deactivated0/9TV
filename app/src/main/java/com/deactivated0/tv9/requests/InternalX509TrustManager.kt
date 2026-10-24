package com.deactivated0.tv9.requests

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

//this code can use https://badssl.com to test
//it can deal with self-signed/expired/wrong host/untrusted root certificate
//it can't deal with certificate pinning and revoked certificate
//latest pem file can be downloaded from https://curl.se/ca/cacert.pem
class InternalX509TrustManager(private val context: Context) : X509TrustManager {
    private val TAG = "CustomX509TrustManager"
    private val delegateTrustManager: X509TrustManager

    init {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        val certificateFactory = CertificateFactory.getInstance("X.509")
        try {
            BufferedInputStream(context.assets.open("cacert.pem")).use { bis ->
                val certs = certificateFactory.generateCertificates(bis)
                var index = 0
                for (cert in certs) {
                    if (cert is X509Certificate) {
                        keyStore.setCertificateEntry("ca$index", cert)
                        index++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading certificates from assets", e)
        }

        val trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        val trustManagers: Array<TrustManager> = trustManagerFactory.trustManagers
        delegateTrustManager = trustManagers[0] as X509TrustManager
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegateTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegateTrustManager.checkServerTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return delegateTrustManager.acceptedIssuers
    }
}