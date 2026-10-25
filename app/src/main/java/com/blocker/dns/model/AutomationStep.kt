package com.blocker.dns.model

enum class AutomationStep(val title: String) {
    IDLE("Ready"),
    PRIVATE_DNS("Private DNS Setup"),
    WIFI_DNS("Wi-Fi DNS Setup"),
    CHROME_DOH("Chrome Secure DNS Setup"),
    BRAVE_DOH("Brave Secure DNS Setup"),
    COMPLETED("Enforcement Completed"),
    FAILED("Automation Failed")
}

sealed class StepState {
    object Pending : StepState()
    object Running : StepState()
    data class Success(val message: String) : StepState()
    data class Failure(val error: String) : StepState()
}
