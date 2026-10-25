package com.blocker.dns.model

object CleanBrowsingConfig {
    const val PRIVATE_DNS_HOSTNAME: String = "family-filter-dns.cleanbrowsing.org"
    const val WIFI_DNS_PRIMARY: String = "185.228.168.168"
    const val WIFI_DNS_SECONDARY: String = "185.228.169.168"
    const val DOH_URL: String = "https://doh.cleanbrowsing.org/doh/family-filter/"
    const val PROVIDER_NAME: String = "CleanBrowsing (Family Filter)"

    // Keywords in multiple languages (English & Arabic) to find UI nodes dynamically
    val PRIVATE_DNS_KEYWORDS = listOf(
        "Private DNS",
        "اسم النطاق الخاص",
        "نظام أسماء النطاقات الخاص",
        "DNS الخاص",
        "Private DNS provider hostname",
        "اسم مضيف موفر نظام أسماء النطاقات الخاص"
    )

    val WIFI_KEYWORDS = listOf(
        "Wi-Fi",
        "الواي فاي",
        "شبكة Wi-Fi",
        "IP settings",
        "إعدادات IP",
        "Static",
        "ثابت",
        "DNS 1",
        "DNS 2"
    )

    val CHROME_KEYWORDS = listOf(
        "Settings",
        "الإعدادات",
        "Privacy and security",
        "الخصوصية والأمان",
        "Use secure DNS",
        "استخدام نظام أسماء نطاقات آمن",
        "Choose another provider",
        "اختيار موفّر آخر",
        "Custom",
        "مخصص"
    )

    val BRAVE_KEYWORDS = listOf(
        "Settings",
        "الإعدادات",
        "Brave Shields & privacy",
        "دروع Brave والخصوصية",
        "Use secure DNS",
        "استخدام نظام أسماء نطاقات آمن",
        "Choose another provider",
        "اختيار موفّر آخر",
        "Custom",
        "مخصص"
    )

    val SAVE_BUTTON_KEYWORDS = listOf(
        "Save",
        "حفظ",
        "Done",
        "تم",
        "Apply",
        "تطبيق",
        "OK",
        "موافق"
    )
}
