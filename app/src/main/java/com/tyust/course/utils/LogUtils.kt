package com.tyust.course.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object LogUtils {
    private val exportMutex = Mutex()

    suspend fun exportLogs(context: Context) {
        if (!exportMutex.tryLock()) return
        try {
            val logFile = withContext(Dispatchers.IO) {
                val logDir = File(context.cacheDir, "logs")
                if (!logDir.isDirectory && !logDir.mkdirs()) {
                    throw IOException("Cannot create log export directory")
                }
                val file = File(logDir, "app_log.txt")
                val process = ProcessBuilder(
                    "logcat", "-d", "--pid=${android.os.Process.myPid()}"
                ).redirectErrorStream(true).start()
                try {
                    process.inputStream.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (process.waitFor() != 0) throw IOException("Log export failed")
                } finally {
                    process.destroy()
                }
                file
            }
            withContext(Dispatchers.Main.immediate) { shareFile(context, logFile) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                Toast.makeText(context, "日志导出失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        } finally {
            exportMutex.unlock()
        }
    }

    private fun shareFile(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "导出日志"))
    }
}
