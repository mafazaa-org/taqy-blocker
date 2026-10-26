package com.blocker.dns.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blocker.dns.model.AutomationStep
import com.blocker.dns.service.DnsAutomationService
import com.blocker.dns.steps.BraveDnsStep
import com.blocker.dns.steps.ChromeDnsStep
import com.blocker.dns.steps.PrivateDnsStep
import com.blocker.dns.steps.WifiDnsStep

class AutomationStateMachine(private val context: Context) {
    private val TAG = "AutomationStateMachine"

    interface AutomationListener {
        fun onStepChanged(step: AutomationStep, message: String)
        fun onLog(message: String)
        fun onCompleted()
        fun onError(step: AutomationStep, error: String)
    }

    private var listener: AutomationListener? = null
    var currentStep: AutomationStep = AutomationStep.IDLE
        private set

    private var isTransitioning = false
    private var stepWatchdog: Runnable? = null

    private val privateDnsStep = PrivateDnsStep()
    private val wifiDnsStep = WifiDnsStep()
    private val chromeDnsStep = ChromeDnsStep()
    private val braveDnsStep = BraveDnsStep()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setListener(listener: AutomationListener?) {
        this.listener = listener
    }

    private fun postLog(msg: String) {
        LogExporter.log(msg)
        listener?.onLog(msg)
    }

    private var periodicScanRunnable: Runnable? = null
    private var lastDumpHash: Int = 0
    private var lastIntentLaunchTime = 0L

    fun start() {
        Log.d(TAG, "Starting DNS enforcement pipeline...")
        postLog("بدء تشغيل خط أنابيب الحماية...")
        lastDumpHash = 0
        lastIntentLaunchTime = System.currentTimeMillis()
        startPeriodicScanner()
        transitionTo(AutomationStep.PRIVATE_DNS)
    }

    fun stop() {
        cancelWatchdog()
        stopPeriodicScanner()
        currentStep = AutomationStep.IDLE
        isTransitioning = false
        postLog("تم إيقاف الأتمتة.")
    }

    private fun startPeriodicScanner() {
        stopPeriodicScanner()
        periodicScanRunnable = object : Runnable {
            override fun run() {
                if (currentStep != AutomationStep.IDLE && currentStep != AutomationStep.COMPLETED) {
                    val service = DnsAutomationService.instance
                    val root = service?.rootInActiveWindow
                    if (root != null) {
                        val screenDump = NodeHelper.dumpScreen(root, maxNodes = 50)
                        val dumpHash = screenDump.hashCode()
                        if (dumpHash != lastDumpHash) {
                            lastDumpHash = dumpHash
                            LogExporter.log("[فحص الشاشة] $screenDump")
                        }
                        onAccessibilityNode(root)
                    } else {
                        LogExporter.log("[فحص الشاشة] جاري انتظار استجابة الشاشة (rootInActiveWindow=null)...")
                        retryCurrentStepIntentIfNeeded()
                    }
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.postDelayed(periodicScanRunnable!!, 600L)
    }

    private fun stopPeriodicScanner() {
        periodicScanRunnable?.let { mainHandler.removeCallbacks(it) }
        periodicScanRunnable = null
    }

    private fun retryCurrentStepIntentIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastIntentLaunchTime < 3500) return
        lastIntentLaunchTime = now

        when (currentStep) {
            AutomationStep.PRIVATE_DNS -> {
                LogExporter.log("[StateMachine] إعادة محاولة إطلاق إعدادات Private DNS (النافذة المطلوبة لم تظهر بعد)...")
                IntentLauncher.launchPrivateDnsSettings(context)
            }
            AutomationStep.WIFI_DNS -> {
                LogExporter.log("[StateMachine] إعادة محاولة إطلاق إعدادات Wi-Fi (النافذة المطلوبة لم تظهر بعد)...")
                IntentLauncher.launchWifiSettings(context)
            }
            AutomationStep.CHROME_DOH -> {
                LogExporter.log("[StateMachine] إعادة محاولة إطلاق Google Chrome (النافذة المطلوبة لم تظهر بعد)...")
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                mainHandler.postDelayed({
                    IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_CHROME)
                }, 300)
            }
            AutomationStep.BRAVE_DOH -> {
                LogExporter.log("[StateMachine] إعادة محاولة إطلاق Brave Browser (النافذة المطلوبة لم تظهر بعد)...")
                DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                mainHandler.postDelayed({
                    IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_BRAVE)
                }, 300)
            }
            else -> Unit
        }
    }

    fun onAccessibilityNode(root: AccessibilityNodeInfo?) {
        if (root == null) {
            LogExporter.log("[StateMachine] onAccessibilityNode: root is null")
            return
        }
        if (isTransitioning) {
            LogExporter.log("[StateMachine] onAccessibilityNode: جاري الانتقال بين المراحل (step=$currentStep)")
            return
        }
        if (currentStep == AutomationStep.IDLE || currentStep == AutomationStep.COMPLETED) {
            return
        }

        val pkg = root.packageName?.toString() ?: ""

        when (currentStep) {
            AutomationStep.PRIVATE_DNS -> {
                if (pkg == "com.android.settings" || pkg.isEmpty()) {
                    privateDnsStep.processNode(root)
                    if (privateDnsStep.isDone()) {
                        cancelWatchdog()
                        postLog("✓ تم تأمين وتفعيل الطبقة الأولى بنجاح.")
                        transitionTo(AutomationStep.WIFI_DNS)
                    }
                } else {
                    LogExporter.log("[StateMachine] خطوة PRIVATE_DNS تنتظر إعدادات النظام، الشاشة الحالية: '$pkg'")
                    retryCurrentStepIntentIfNeeded()
                }
            }

            AutomationStep.WIFI_DNS -> {
                if (pkg == "com.android.settings" || pkg.isEmpty()) {
                    wifiDnsStep.processNode(root)
                    if (wifiDnsStep.isDone()) {
                        cancelWatchdog()
                        postLog("✓ تم تأمين وتفعيل الطبقة الثانية بنجاح.")
                        DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        transitionTo(AutomationStep.CHROME_DOH)
                    }
                } else {
                    LogExporter.log("[StateMachine] خطوة WIFI_DNS تنتظر إعدادات Wi-Fi، الشاشة الحالية: '$pkg'")
                    retryCurrentStepIntentIfNeeded()
                }
            }

            AutomationStep.CHROME_DOH -> {
                if (pkg.contains("chrome") || (pkg.contains("browser") && !pkg.contains("brave")) || pkg.isEmpty()) {
                    chromeDnsStep.processNode(root)
                    if (chromeDnsStep.isDone()) {
                        cancelWatchdog()
                        postLog("✓ تم تأمين وتفعيل الطبقة الثالثة بنجاح.")
                        DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        transitionTo(AutomationStep.BRAVE_DOH)
                    }
                } else {
                    LogExporter.log("[StateMachine] خطوة CHROME_DOH تنتظر نافذة Chrome، الشاشة الحالية: '$pkg'")
                    retryCurrentStepIntentIfNeeded()
                }
            }

            AutomationStep.BRAVE_DOH -> {
                if (pkg.contains("brave") || pkg.isEmpty()) {
                    braveDnsStep.processNode(root)
                    if (braveDnsStep.isDone()) {
                        cancelWatchdog()
                        postLog("✓ تم تأمين وتفعيل الطبقة الرابعة بنجاح.")
                        DnsAutomationService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        transitionTo(AutomationStep.COMPLETED)
                    }
                } else {
                    LogExporter.log("[StateMachine] خطوة BRAVE_DOH تنتظر نافذة Brave، الشاشة الحالية: '$pkg'")
                    retryCurrentStepIntentIfNeeded()
                }
            }

            else -> Unit
        }
    }

    private fun startWatchdog(step: AutomationStep) {
        cancelWatchdog()
        LogExporter.log("[StateMachine] Watchdog started for step $step (25s)")
        stepWatchdog = Runnable {
            if (currentStep == step) {
                val issue = "الخطوة ${step.title} استغرقت أكثر من وقتها (25 ثانية) وتوقفت."
                Log.w(TAG, "Step $step timed out after 25s: $issue")
                postLog("⚠ تنبيه: $issue")
                listener?.onError(step, issue)

                // Export and send diagnostic log file to WhatsApp (01003816322)
                LogExporter.sendLogsToWhatsApp(context, issue)

                advanceToNextStep(step)
            }
        }
        mainHandler.postDelayed(stepWatchdog!!, 25000L)
    }

    private fun cancelWatchdog() {
        if (stepWatchdog != null) {
            LogExporter.log("[StateMachine] Watchdog cancelled for step $currentStep")
            mainHandler.removeCallbacks(stepWatchdog!!)
            stepWatchdog = null
        }
    }

    private fun advanceToNextStep(failedStep: AutomationStep) {
        LogExporter.log("[StateMachine] Advancing from timed-out step: $failedStep")
        when (failedStep) {
            AutomationStep.PRIVATE_DNS -> {
                transitionTo(AutomationStep.WIFI_DNS)
            }
            AutomationStep.WIFI_DNS -> {
                transitionTo(AutomationStep.CHROME_DOH)
            }
            AutomationStep.CHROME_DOH -> {
                transitionTo(AutomationStep.BRAVE_DOH)
            }
            AutomationStep.BRAVE_DOH -> {
                transitionTo(AutomationStep.COMPLETED)
            }
            else -> Unit
        }
    }

    private fun transitionTo(nextStep: AutomationStep) {
        cancelWatchdog()
        isTransitioning = true
        LogExporter.log("[StateMachine] Transitioning from $currentStep to $nextStep")
        currentStep = nextStep
        listener?.onStepChanged(nextStep, nextStep.title)

        mainHandler.postDelayed({
            when (nextStep) {
                AutomationStep.PRIVATE_DNS -> {
                    privateDnsStep.reset()
                    postLog("جاري معالجة وتثبيت الطبقة الأولى...")
                    IntentLauncher.launchPrivateDnsSettings(context)
                    startWatchdog(nextStep)
                }

                AutomationStep.WIFI_DNS -> {
                    wifiDnsStep.reset()
                    postLog("جاري معالجة وتثبيت الطبقة الثانية...")
                    IntentLauncher.launchWifiSettings(context)
                    startWatchdog(nextStep)
                }

                AutomationStep.CHROME_DOH -> {
                    chromeDnsStep.reset()
                    postLog("جاري فحص وتثبيت الطبقة الثالثة...")
                    val launched = IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_CHROME)
                    if (!launched) {
                        postLog("الطبقة الثالثة غير مطلوبة، الانتقال للطبقة التالية...")
                        transitionTo(AutomationStep.BRAVE_DOH)
                        return@postDelayed
                    }
                    startWatchdog(nextStep)
                }

                AutomationStep.BRAVE_DOH -> {
                    braveDnsStep.reset()
                    postLog("جاري فحص وتثبيت الطبقة الرابعة...")
                    val launched = IntentLauncher.launchApp(context, IntentLauncher.PACKAGE_BRAVE)
                    if (!launched) {
                        postLog("الطبقة الرابعة غير مطلوبة، إنهاء التفعيل...")
                        transitionTo(AutomationStep.COMPLETED)
                        return@postDelayed
                    }
                    startWatchdog(nextStep)
                }

                AutomationStep.COMPLETED -> {
                    cancelWatchdog()
                    stopPeriodicScanner()
                    postLog("اكتملت جميع طبقات الحماية بنجاح! درع الحماية نشط.")
                    listener?.onCompleted()
                    try {
                        val returnIntent = android.content.Intent(context, com.blocker.dns.MainActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(returnIntent)
                    } catch (_: Exception) {}
                }

                else -> Unit
            }

            // Settle delay before processing events of the new window
            mainHandler.postDelayed({
                isTransitioning = false
            }, 400)

        }, 400)
    }
}
