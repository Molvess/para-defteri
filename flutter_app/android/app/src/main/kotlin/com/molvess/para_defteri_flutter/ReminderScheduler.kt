package com.molvess.para_defteri_flutter

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object ReminderScheduler {
    private const val CHANNEL = "debt_reminders"
    private fun prefs(c: Context) = c.getSharedPreferences("debt_reminders", Context.MODE_PRIVATE)
    private fun alarms(c: Context) = c.getSystemService(AlarmManager::class.java)
    private fun notifications(c: Context) = c.getSystemService(NotificationManager::class.java)
    fun channel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) notifications(c).createNotificationChannel(
            NotificationChannel(CHANNEL, "Borç hatırlatıcıları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Seçtiğiniz tarih ve saatte yerel borç hatırlatmaları"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
    }
    fun status(c: Context): Map<String, Boolean> {
        channel(c)
        val allowed = notifications(c).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < 26 || notifications(c).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE)
        return mapOf("notifications" to allowed, "exact" to (Build.VERSION.SDK_INT < 31 || alarms(c).canScheduleExactAlarms()))
    }
    private fun pending(c: Context, id: String): PendingIntent = PendingIntent.getBroadcast(c, 0,
        Intent(c, ReminderReceiver::class.java).setAction("com.molvess.ledger.REMIND")
            .setData(Uri.Builder().scheme("ledger-reminder").authority("debt").appendPath(id).build())
            .putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun sync(c: Context, array: JSONArray): Map<String, Boolean> {
        // Validate before changing any existing alarm.
        val wanted = linkedMapOf<String, JSONObject>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.getString("id")
            require(id.isNotBlank() && !wanted.containsKey(id))
            val r = item.getJSONObject("reminder")
            next(r, System.currentTimeMillis())
            if (r.optBoolean("enabled", true)) wanted[id] = item
        }
        val p = prefs(c)
        for (id in p.all.keys - wanted.keys) {
            alarms(c).cancel(pending(c, id))
            notifications(c).cancel(id, 0)
            p.edit().remove(id).commit()
        }
        wanted.forEach { (id, item) ->
            val old = p.getString(id, null)?.let { JSONObject(it) }
            if (old?.optJSONObject("reminder")?.toString() == item.getJSONObject("reminder").toString()) {
                item.put("lastFire", old.optLong("lastFire", 0))
            }
            check(p.edit().putString(id, item.toString()).commit())
            schedule(c, id, item)
        }
        return status(c)
    }
    private fun next(r: JSONObject, after: Long): Long? = ReminderTime.next(
        r.getString("startDate"), r.getInt("hour"), r.getInt("minute"),
        r.optString("repeat", "once"), r.optInt("interval", 1),
        if (r.isNull("endDate")) null else r.getString("endDate"), after)

    private fun schedule(c: Context, id: String, item: JSONObject) {
        val intent = pending(c, id)
        alarms(c).cancel(intent)
        val at = next(item.getJSONObject("reminder"), maxOf(System.currentTimeMillis(), item.optLong("lastFire", 0)))
        item.put("scheduledAt", at ?: 0)
        check(prefs(c).edit().putString(id, item.toString()).commit())
        if (at == null) return
        if (Build.VERSION.SDK_INT < 31 || alarms(c).canScheduleExactAlarms()) {
            try { alarms(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent); return }
            catch (_: SecurityException) { /* Permission may have been revoked mid-call. */ }
        }
        alarms(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    @Synchronized fun restore(c: Context) {
        prefs(c).all.forEach { (id, raw) -> runCatching { schedule(c, id, JSONObject(raw as String)) } }
    }

    @Synchronized fun fire(c: Context, id: String) {
        val item = prefs(c).getString(id, null)?.let { JSONObject(it) } ?: return
        val due = item.optLong("scheduledAt", 0)
        if (due == 0L || due <= item.optLong("lastFire", 0)) return
        if (System.currentTimeMillis() < due) { schedule(c, id, item); return }
        item.put("lastFire", due)
        check(prefs(c).edit().putString(id, item.toString()).commit())
        if (status(c)["notifications"] == true) {
            val launch = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CHANNEL) else Notification.Builder(c)
            val notification = builder.setSmallIcon(com.molvess.para_defteri_flutter.R.drawable.ic_reminder)
                .setContentTitle("Para Defteri · Hatırlatıcı").setContentText(item.getString("body"))
                .setStyle(Notification.BigTextStyle().bigText(item.getString("body")))
                .setContentIntent(launch).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
            try { notifications(c).notify(id, 0, notification) } catch (_: SecurityException) { }
        }
        schedule(c, id, item)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                if (intent.action == "com.molvess.ledger.REMIND") {
                    intent.getStringExtra("id")?.let { ReminderScheduler.fire(context, it) }
                } else ReminderScheduler.restore(context)
            } catch (_: Exception) { /* Preserve definitions for the next app sync. */ }
            finally { result.finish() }
        }.start()
    }
}
