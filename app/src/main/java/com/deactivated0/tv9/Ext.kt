@file:Suppress("DEPRECATION")

package com.deactivated0.tv9

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.security.MessageDigest
import java.util.regex.Pattern

private const val TAG = "Extensions"

private val HEX_CHARS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
)

private val IPV6_URL_PATTERN: Pattern = Pattern.compile(
    "^((http|https)://)?(\\[[0-9a-fA-F:]+])(:[0-9]+)?(/.*)?$"
)

@Volatile
private var cachedPackageInfo: PackageInfo? = null

@Volatile
private var cachedSignatureHash: String? = null

private val Context.packageInfo: PackageInfo
    get() {
        cachedPackageInfo?.let { return it }
        val flag = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNATURES
        } else {
            PackageManager.GET_SIGNING_CERTIFICATES
        }
        val info = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, flag)
        } else {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        }
        cachedPackageInfo = info
        return info
    }

/**
 * Return the version code of the app which is defined in build.gradle.
 * eg:100
 */
val Context.appVersionCode: Long
    get() {
        val packageInfo = this.packageInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

/**
 * Return the version name of the app which is defined in build.gradle.
 * eg:1.0.0
 */
val Context.appVersionName: String get() = packageInfo.versionName ?: "1.0.0"

val Context.appSignature: String
    get() {
        cachedSignatureHash?.let { return it }
        val packageInfo = this.packageInfo
        var sign: Signature? = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val signatures: Array<out Signature>? = packageInfo.signatures
            if (!signatures.isNullOrEmpty()) {
                sign = signatures[0]
            }
        } else {
            val signingInfo: SigningInfo? = packageInfo.signingInfo
            if (signingInfo != null) {
                val signers = signingInfo.apkContentsSigners
                if (!signers.isNullOrEmpty()) {
                    sign = signers[0]
                }
            }
        }
        if (sign == null) {
            return ""
        }
        val hash = hashSignature(sign)
        cachedSignatureHash = hash
        return hash
    }

private fun bytesToHex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var idx = 0
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        result[idx++] = HEX_CHARS[v ushr 4]
        result[idx++] = HEX_CHARS[v and 0x0F]
    }
    return String(result)
}

private fun hashSignature(signature: Signature): String {
    return try {
        val md = MessageDigest.getInstance("MD5")
        md.update(signature.toByteArray())
        bytesToHex(md.digest())
    } catch (e: Exception) {
        Log.e(TAG, "Error hashing signature", e)
        ""
    }
}

fun String.showToast(duration: Int = Toast.LENGTH_SHORT) {
    MyTVApplication.getInstance().toast(this, duration)
}

fun Int.getString(): String {
    return MyTVApplication.getInstance().getString(this)
}

fun Int.showToast(duration: Int = Toast.LENGTH_SHORT) {
    this.getString().showToast(duration)
}

fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.toByteArray(Charsets.UTF_8))
    return bytesToHex(digest)
}

fun String.isIPv6(): Boolean {
    if (this.indexOf('[') == -1) return false
    return IPV6_URL_PATTERN.matcher(this).matches()
}