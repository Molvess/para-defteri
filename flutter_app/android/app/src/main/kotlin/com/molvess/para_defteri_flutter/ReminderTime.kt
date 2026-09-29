package com.molvess.para_defteri_flutter

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal object ReminderTime {
    fun next(start: String, hour: Int, minute: Int, repeat: String, interval: Int,
             end: String?, after: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        require(hour in 0..23 && minute in 0..59 && interval in 1..365)
        require(repeat in listOf("once", "daily", "weekly", "monthly"))
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false; timeZone = zone }
        val base = Calendar.getInstance(zone).apply {
            time = requireNotNull(format.parse(start))
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val limit = end?.let { Calendar.getInstance(zone).apply {
            time = requireNotNull(format.parse(it)); set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis } ?: Long.MAX_VALUE
        require(limit >= base.timeInMillis)
        if (repeat == "once") return base.timeInMillis.takeIf { it > after && it <= limit }
        val originalDay = base.get(Calendar.DAY_OF_MONTH)
        // Calendar arithmetic preserves local wall time across DST. Month-end
        // clamps to the last day but returns to the original day next month.
        for (index in 0..120000) {
            val candidate = (base.clone() as Calendar).apply {
                if (repeat == "monthly") {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, index * interval)
                    set(Calendar.DAY_OF_MONTH, minOf(originalDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                } else add(Calendar.DAY_OF_YEAR, index * interval * if (repeat == "weekly") 7 else 1)
            }.timeInMillis
            if (candidate > limit) return null
            if (candidate > after) return candidate
        }
        return null
    }
}
