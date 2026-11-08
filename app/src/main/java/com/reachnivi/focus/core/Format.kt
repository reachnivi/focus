package com.reachnivi.focus.core

object Format {
    /** 2h 14m, 45m, <1m */
    fun duration(ms: Long): String {
        val totalMinutes = ms / 60_000
        if (totalMinutes < 1) return if (ms > 0) "<1m" else "0m"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** 24:59 or 1:04:59 for countdowns. */
    fun clock(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun minuteOfDay(minute: Int): String {
        val h = (minute / 60) % 24
        val m = minute % 60
        return "%02d:%02d".format(h, m)
    }
}
