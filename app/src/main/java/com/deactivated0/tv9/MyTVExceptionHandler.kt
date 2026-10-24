package com.deactivated0.tv9

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

class MyTVExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        val crashInfo =
            "APP: ${context.appVersionName}, PRODUCT: ${Build.PRODUCT}, DEVICE: ${Build.DEVICE}, SUPPORTED_ABIS: ${Build.CPU_ABI},${Build.CPU_ABI2}, BOARD: ${Build.BOARD}, MANUFACTURER: ${Build.MANUFACTURER}, MODEL: ${Build.MODEL}, VERSION: ${Build.VERSION.SDK_INT}\nThread: ${t.name}\nException: ${e.message}\nStackTrace: ${
                Log.getStackTraceString(
                    e
                )
            }\n"

        Log.e(TAG, crashInfo)
        AppLogger.e(TAG, "Uncaught exception crash:\n$crashInfo", e)
        try {
            File(context.filesDir, "crash.log").writeText(crashInfo)
        } catch (ignored: Exception) {
        }

        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }

    companion object {
        private const val TAG = "MyTVException"
    }
}