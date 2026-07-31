package com.silentlink.app.model

enum class RestoreDuration(val ms: Long, val label: String) {
    FIVE_MIN(5 * 60 * 1000L, "5분"),
    TEN_MIN(10 * 60 * 1000L, "10분"),
    UNLIMITED(Long.MAX_VALUE, "무제한")
}
