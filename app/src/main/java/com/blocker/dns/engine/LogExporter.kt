package com.blocker.dns.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {
    private const val TAG = "LogExporter"
    const val WHATSAPP_PHONE_RAW = "01003816322"
    const val WHATSAPP_PHONE_INTL = "201003816322"
    const val WHATSAPP_LINK = "https://wa.me/$WHATSAPP_PHONE_INTL"

    private val logMemoryBuffer = mutableListOf<String>()

    fun log(msg: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $msg"
        synchronized(logMemoryBuffer) {
            logMemoryBuffer.add(formatted)
            if (logMemoryBuffer.size > 5000) {
                logMemoryBuffer.removeAt(0)
            }
        }
        Log.d(TAG, formatted)
    }

    fun getAllLogs(): List<String> {
        return synchronized(logMemoryBuffer) {
            logMemoryBuffer.toList()
        }
    }

    fun generateLogFile(context: Context, issueDescription: String): File {
        val logFile = File(context.cacheDir, "taqi_diagnostic_log.txt")
        FileWriter(logFile, false).use { writer ->
            writer.write("========================================\n")
            writer.write("سجل تشخيص تطبيق تَقِيّ\n")
            writer.write("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            writer.write("المشكلة: $issueDescription\n")
            writer.write("رابط الدعم: $WHATSAPP_LINK\n")
            writer.write("========================================\n")
            writer.write("معلومات الجهاز:\n")
            writer.write("الشركة المصنعة: ${Build.MANUFACTURER}\n")
            writer.write("الموديل: ${Build.MODEL} (${Build.DEVICE})\n")
            writer.write("إصدار أندرويد: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            writer.write("واجهة المستخدم: ${Build.DISPLAY}\n")
            writer.write("========================================\n\n")
            writer.write("--- تفاصيل السجل والعمليات ---\n")
            synchronized(logMemoryBuffer) {
                for (entry in logMemoryBuffer) {
                    writer.write(entry + "\n")
                }
            }
            writer.write("\n========================================\n")
            writer.write("نهاية التقرير\n")
        }
        return logFile
    }

    /**
     * Opens direct WhatsApp / WhatsApp Business chat via wa.me link.
     */
    fun openWhatsAppChatLink(context: Context, message: String = "") {
        val text = if (message.isNotBlank()) message else "السلام عليكم، أحتاج مساعدة في تطبيق تَقِيّ"
        val waUrl = "$WHATSAPP_LINK?text=" + Uri.encode(text)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.d(TAG, "Opened WhatsApp link: $waUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening WhatsApp link: ${e.message}", e)
        }
    }

    /**
     * Shares the diagnostic file via standard system chooser allowing WhatsApp and WhatsApp Business.
     */
    fun shareLogFile(context: Context, issueDescription: String) {
        try {
            val file = generateLogFile(context, issueDescription)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareText = "السلام عليكم، تقرير تشخيص تطبيق تَقِيّ:\n$issueDescription\nرابط المحادثة: $WHATSAPP_LINK"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "إرسال ملف السجل (واتساب / واتساب للأعمال)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Log.d(TAG, "Launched log file chooser for WhatsApp / WhatsApp Business")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sharing log file: ${e.message}", e)
            openWhatsAppChatLink(context, "تقرير مشكلة: $issueDescription")
        }
    }

    fun sendLogsToWhatsApp(context: Context, issueDescription: String) {
        shareLogFile(context, issueDescription)
    }
}
