package com.deactivated0.tv9

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOG_SIZE_BYTES = 1024 * 1024L // 1 MB
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val externalDir = context.getExternalFilesDir(null)
            val baseDir = if (externalDir != null && (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable())) {
                externalDir
            } else {
                context.filesDir
            }
            logFile = File(baseDir, "9tv.log")

            // Install uncaught exception crash handler
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    val crashInfo = buildString {
                        appendLine("FATAL EXCEPTION on thread ${thread.name} (id ${thread.id}):")
                        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Heap: free=${Runtime.getRuntime().freeMemory() / 1024}KB, total=${Runtime.getRuntime().totalMemory() / 1024}KB, max=${Runtime.getRuntime().maxMemory() / 1024}KB")
                        appendLine("Stack Trace:\n$sw")
                    }
                    writeDirect("CRASH", crashInfo)
                } catch (t: Throwable) {
                    t.printStackTrace()
                } finally {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }

            i(TAG, "AppLogger initialized. Log file: ${logFile?.absolutePath}")
            i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize AppLogger", e)
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("INFO", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
        write("WARN", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        write("ERROR", tag, message, throwable)
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val target = logFile ?: return
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val trace = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "\n$sw"
        } else {
            ""
        }
        val line = "[$timestamp] [$level/$tag] $message$trace\n"
        executor.execute {
            try {
                target.parentFile?.mkdirs()
                rotateIfNeeded(target)
                FileWriter(target, true).use { it.write(line) }
            } catch (ignored: Throwable) {}
        }
    }

    private fun writeDirect(tag: String, message: String) {
        val target = logFile ?: return
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val line = "[$timestamp] [$tag] $message\n"
        try {
            target.parentFile?.mkdirs()
            rotateIfNeeded(target)
            FileWriter(target, true).use {
                it.write(line)
                it.flush()
            }
        } catch (ignored: Throwable) {}
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
            val oldFile = File(file.parentFile, "${file.name}.old")
            if (oldFile.exists()) oldFile.delete()
            file.renameTo(oldFile)
        }
    }

    fun getLogPath(): String? = logFile?.absolutePath
}
