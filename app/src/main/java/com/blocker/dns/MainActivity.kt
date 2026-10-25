package com.blocker.dns

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.blocker.dns.databinding.ActivityMainBinding
import com.blocker.dns.engine.AutomationStateMachine
import com.blocker.dns.engine.IntentLauncher
import com.blocker.dns.model.AutomationStep
import com.blocker.dns.service.DnsAutomationService

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
            IntentLauncher.launchAccessibilitySettings(this)
        }

        binding.btnStartAutomation.setOnClickListener {
            val service = DnsAutomationService.instance
            if (service == null) {
                Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
                IntentLauncher.launchAccessibilitySettings(this)
                return@setOnClickListener
            }

            service.stateMachine.setListener(this)
            resetStepIndicators()
            appendLog("Starting automated DNS enforcement...")
            service.stateMachine.start()
        }
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
        binding.tvStepPrivateDns.text = "1. Private DNS: Pending"
        binding.tvStepWifiDns.text = "2. Wi-Fi DNS: Pending"
        binding.tvStepChromeDns.text = "3. Chrome DoH: Pending"
        binding.tvStepBraveDns.text = "4. Brave DoH: Pending"
    }

    override fun onStepChanged(step: AutomationStep, message: String) {
        runOnUiThread {
            when (step) {
                AutomationStep.PRIVATE_DNS -> {
                    binding.tvStepPrivateDns.text = "1. Private DNS: In Progress..."
                }
                AutomationStep.WIFI_DNS -> {
                    binding.tvStepPrivateDns.text = "1. Private DNS: Completed \u2714"
                    binding.tvStepWifiDns.text = "2. Wi-Fi DNS: In Progress..."
                }
                AutomationStep.CHROME_DOH -> {
                    binding.tvStepWifiDns.text = "2. Wi-Fi DNS: Completed \u2714"
                    binding.tvStepChromeDns.text = "3. Chrome DoH: In Progress..."
                }
                AutomationStep.BRAVE_DOH -> {
                    binding.tvStepChromeDns.text = "3. Chrome DoH: Completed \u2714"
                    binding.tvStepBraveDns.text = "4. Brave DoH: In Progress..."
                }
                AutomationStep.COMPLETED -> {
                    binding.tvStepBraveDns.text = "4. Brave DoH: Completed \u2714"
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
            appendLog("\u2705 ALL STEPS COMPLETED. CleanBrowsing Family Filter active.")
            Toast.makeText(this, "CleanBrowsing protection active!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onError(step: AutomationStep, error: String) {
        runOnUiThread {
            appendLog("\u274C Error in ${step.title}: $error")
            Toast.makeText(this, "Error in ${step.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun appendLog(msg: String) {
        logBuilder.append("• ").append(msg).append("\n")
        binding.tvLogOutput.text = logBuilder.toString()
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
