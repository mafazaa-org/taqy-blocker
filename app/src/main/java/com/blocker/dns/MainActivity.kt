package com.blocker.dns

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blocker.dns.databinding.ActivityMainBinding
import com.blocker.dns.engine.AutomationStateMachine
import com.blocker.dns.engine.IntentLauncher
import com.blocker.dns.engine.LogExporter
import com.blocker.dns.model.AutomationStep
import com.blocker.dns.service.DnsAutomationService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), AutomationStateMachine.AutomationListener {

    private lateinit var binding: ActivityMainBinding
    private val logBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupListeners() {
        binding.btnEnableService.setOnClickListener {
            showAccessibilityGuideDialog()
        }

        binding.btnStartAutomation.setOnClickListener {
            val service = DnsAutomationService.instance
            if (service == null) {
                Toast.makeText(this, getString(R.string.toast_enable_service), Toast.LENGTH_SHORT).show()
                showAccessibilityGuideDialog()
                return@setOnClickListener
            }

            showPreStartWarningDialog(service)
        }

        binding.btnSendLogsWhatsApp.setOnClickListener {
            showWhatsAppOptionsDialog()
        }
    }

    private fun showWhatsAppOptionsDialog() {
        val options = arrayOf(
            "فتح محادثة الدعم عبر الرابط (01003816322)",
            "مشاركة ملف السجل كاملاً (واتساب / واتساب للأعمال)"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("التواصل مع الدعم الفني")
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> LogExporter.openWhatsAppChatLink(this, "السلام عليكم، أحتاج مساعدة في تطبيق تَقِيّ")
                    1 -> LogExporter.shareLogFile(this, "طلب دعم ومراجعة سجلات تفعيل الحماية")
                }
            }
            .setNegativeButton(getString(R.string.pre_start_warning_cancel), null)
            .show()
    }

    private fun showAccessibilityGuideDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.accessibility_guide_title))
            .setMessage(getString(R.string.accessibility_guide_message))
            .setPositiveButton(getString(R.string.accessibility_guide_button)) { dialog, _ ->
                dialog.dismiss()
                IntentLauncher.launchAccessibilitySettings(this)
            }
            .setNegativeButton(getString(R.string.pre_start_warning_cancel), null)
            .show()
    }

    private fun showPreStartWarningDialog(service: DnsAutomationService) {
        val customView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 30)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val headlineView = TextView(this).apply {
            text = getString(R.string.pre_start_warning_headline)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.gold_accent))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 24)
        }

        val bodyView = TextView(this).apply {
            text = getString(R.string.pre_start_warning_body)
            textSize = 16f
            gravity = Gravity.START
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setLineSpacing(12f, 1.15f)
        }

        customView.addView(headlineView)
        customView.addView(bodyView)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pre_start_warning_title))
            .setView(customView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.pre_start_warning_confirm)) { dialog, _ ->
                dialog.dismiss()
                startCountdownAndLaunch(service)
            }
            .setNegativeButton(getString(R.string.pre_start_warning_cancel), null)
            .show()
    }

    private fun startCountdownAndLaunch(service: DnsAutomationService) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 60, 48, 60)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val headlineView = TextView(this).apply {
            text = getString(R.string.countdown_headline)
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.gold_accent))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }

        val subtitleView = TextView(this).apply {
            text = getString(R.string.countdown_subtitle)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            setPadding(0, 0, 0, 30)
        }

        val countView = TextView(this).apply {
            text = "10"
            textSize = 72f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.accent_green))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        layout.addView(headlineView)
        layout.addView(subtitleView)
        layout.addView(countView)

        val countdownDialog = MaterialAlertDialogBuilder(this)
            .setView(layout)
            .setCancelable(false)
            .create()

        countdownDialog.show()

        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000 + 1).toInt()
                countView.text = secondsRemaining.toString()
            }

            override fun onFinish() {
                if (!isFinishing && !isDestroyed) {
                    countdownDialog.dismiss()
                }
                service.stateMachine.setListener(this@MainActivity)
                resetStepIndicators()
                appendLog("بدء تفعيل طبقات الحماية المتتالية...")
                service.stateMachine.start()
            }
        }.start()
    }

    private fun updateServiceStatus() {
        val isServiceRunning = isAccessibilityServiceEnabled(this, DnsAutomationService::class.java)

        if (isServiceRunning && DnsAutomationService.isRunning()) {
            binding.tvServiceStatus.text = getString(R.string.service_status_active)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnEnableService.visibility = View.GONE
            binding.btnStartAutomation.isEnabled = true
            DnsAutomationService.instance?.stateMachine?.setListener(this)
        } else {
            binding.tvServiceStatus.text = getString(R.string.service_status_inactive)
            binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            binding.btnEnableService.visibility = View.VISIBLE
            binding.btnStartAutomation.isEnabled = false
        }
    }

    private fun resetStepIndicators() {
        val pending = getString(R.string.status_pending)
        val defaultColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.tvStep1Status.text = pending
        binding.tvStep1Status.setTextColor(defaultColor)

        binding.tvStep2Status.text = pending
        binding.tvStep2Status.setTextColor(defaultColor)

        binding.tvStep3Status.text = pending
        binding.tvStep3Status.setTextColor(defaultColor)

        binding.tvStep4Status.text = pending
        binding.tvStep4Status.setTextColor(defaultColor)
    }

    override fun onStepChanged(step: AutomationStep, message: String) {
        runOnUiThread {
            val colorProgress = ContextCompat.getColor(this, R.color.accent_blue)
            val colorDone = ContextCompat.getColor(this, R.color.accent_green)
            val textProgress = getString(R.string.status_in_progress)
            val textDone = getString(R.string.status_completed)

            when (step) {
                AutomationStep.PRIVATE_DNS -> {
                    binding.tvStep1Status.text = textProgress
                    binding.tvStep1Status.setTextColor(colorProgress)
                }
                AutomationStep.WIFI_DNS -> {
                    binding.tvStep1Status.text = textDone
                    binding.tvStep1Status.setTextColor(colorDone)
                    binding.tvStep2Status.text = textProgress
                    binding.tvStep2Status.setTextColor(colorProgress)
                }
                AutomationStep.CHROME_DOH -> {
                    binding.tvStep2Status.text = textDone
                    binding.tvStep2Status.setTextColor(colorDone)
                    binding.tvStep3Status.text = textProgress
                    binding.tvStep3Status.setTextColor(colorProgress)
                }
                AutomationStep.BRAVE_DOH -> {
                    binding.tvStep3Status.text = textDone
                    binding.tvStep3Status.setTextColor(colorDone)
                    binding.tvStep4Status.text = textProgress
                    binding.tvStep4Status.setTextColor(colorProgress)
                }
                AutomationStep.COMPLETED -> {
                    binding.tvStep4Status.text = textDone
                    binding.tvStep4Status.setTextColor(colorDone)
                }
                else -> Unit
            }
        }
    }

    override fun onLog(message: String) {
        runOnUiThread {
            appendLog(message)
        }
    }

    override fun onCompleted() {
        runOnUiThread {
            appendLog("✓ اكتملت كافة طبقات الحماية بنجاح.")
            appendLog("♬ إطلاق صوت تغريدة العصفور...")
            playBirdChirp()
            Toast.makeText(this, getString(R.string.toast_success), Toast.LENGTH_LONG).show()

            // Revoke accessibility service after audio starts
            binding.root.postDelayed({
                appendLog("إلغاء إذن إمكانية الوصول لحماية الإعدادات...")
                DnsAutomationService.instance?.disableService()
                updateServiceStatus()
            }, 2500)
        }
    }

    private fun playBirdChirp() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.bird_chirp)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing bird chirp", e)
        }
    }

    override fun onError(step: AutomationStep, error: String) {
        runOnUiThread {
            appendLog("⚠ تنبيه أثناء معالجة ${step.title}: $error")
            Toast.makeText(this, "تنبيه في ${step.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appendLog(msg: String) {
        logBuilder.append("• ").append(msg).append("\n")
        binding.tvLogOutput.text = logBuilder.toString()
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
