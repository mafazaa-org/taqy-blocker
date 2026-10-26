package com.blocker.dns.model

enum class AutomationStep(val title: String) {
    IDLE("جاهز"),
    PRIVATE_DNS("الطبقة الأولى"),
    WIFI_DNS("الطبقة الثانية"),
    CHROME_DOH("الطبقة الثالثة"),
    BRAVE_DOH("الطبقة الرابعة"),
    COMPLETED("اكتمل التفعيل"),
    FAILED("فشل التفعيل")
}

sealed class StepState {
    object Pending : StepState()
    object Running : StepState()
    data class Success(val message: String) : StepState()
    data class Failure(val error: String) : StepState()
}
