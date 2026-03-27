package com.example.moment.data

import android.content.Context
import android.content.SharedPreferences
import com.example.moment.data.entity.DailyAccount
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

object TimeManager {
    private const val PREF_NAME = "MomentPrefs"
    private const val KEY_BALANCE_SECONDS = "balance_seconds"
    private const val KEY_BLOCKED_APPS = "blocked_apps"
    private const val KEY_LAST_RESET_DAY_KEY = "last_reset_day_key"
    private const val KEY_DAILY_BASE_MINUTES = "daily_base_minutes"
    private const val KEY_LAST_DAILY_BASE_UPDATE_AT = "last_daily_base_update_at"
    private const val KEY_CURRENT_FOREGROUND_APP = "current_foreground_app"
    private const val DAILY_BASE_UPDATE_COOLDOWN_MS = 24 * 60 * 60 * 1000L
    private val dbExecutor = Executors.newSingleThreadExecutor()

    data class BaseMinutesUpdateResult(
        val success: Boolean,
        val remainingMillis: Long = 0L
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getBalanceSeconds(context: Context): Int = getPrefs(context).getInt(KEY_BALANCE_SECONDS, 0)

    @Synchronized
    fun addSeconds(context: Context, seconds: Int) {
        if (seconds <= 0) return
        val current = getBalanceSeconds(context)
        val updated = current + seconds
        getPrefs(context).edit().putInt(KEY_BALANCE_SECONDS, updated).apply()
        syncDailyAccount(
            context = context,
            dayKey = getCurrentLogicDayKey(),
            earnedDelta = seconds,
            remainingSeconds = updated
        )
    }

    @Synchronized
    fun setBalanceSeconds(context: Context, seconds: Int) {
        getPrefs(context).edit().putInt(KEY_BALANCE_SECONDS, seconds.coerceAtLeast(0)).apply()
    }


    @Synchronized
    fun consumeSeconds(context: Context, seconds: Int) {
        if (seconds <= 0) return
        val current = getBalanceSeconds(context)
        val updated = (current - seconds).coerceAtLeast(0)
        getPrefs(context).edit().putInt(KEY_BALANCE_SECONDS, updated).apply()
        syncDailyAccount(
            context = context,
            dayKey = getCurrentLogicDayKey(),
            spentDelta = (current - updated).coerceAtLeast(0),
            remainingSeconds = updated
        )
    }

    fun getBlockedApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_BLOCKED_APPS, setOf("com.tencent.mobileqq", "tv.danmaku.bili")) ?: emptySet()
    }

    fun setBlockedApps(context: Context, apps: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
    }

    fun getDailyBaseMinutes(context: Context): Int = getPrefs(context).getInt(KEY_DAILY_BASE_MINUTES, 30)

    fun setDailyBaseMinutes(context: Context, minutes: Int) {
        getPrefs(context).edit().putInt(KEY_DAILY_BASE_MINUTES, minutes).apply()
    }

    fun getDailyBaseUpdateCooldownRemainingMillis(context: Context, nowMillis: Long = System.currentTimeMillis()): Long {
        val lastUpdateMillis = getPrefs(context).getLong(KEY_LAST_DAILY_BASE_UPDATE_AT, 0L)
        if (lastUpdateMillis <= 0L) return 0L
        val availableAt = lastUpdateMillis + DAILY_BASE_UPDATE_COOLDOWN_MS
        return (availableAt - nowMillis).coerceAtLeast(0L)
    }

    fun updateDailyBaseMinutesWithCooldown(
        context: Context,
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): BaseMinutesUpdateResult {
        val prefs = getPrefs(context)
        val remaining = getDailyBaseUpdateCooldownRemainingMillis(context, nowMillis)
        if (remaining > 0L) {
            return BaseMinutesUpdateResult(success = false, remainingMillis = remaining)
        }

        prefs.edit()
            .putInt(KEY_DAILY_BASE_MINUTES, minutes)
            .putLong(KEY_LAST_DAILY_BASE_UPDATE_AT, nowMillis)
            .apply()

        return BaseMinutesUpdateResult(success = true)
    }

    fun setCurrentForegroundApp(context: Context, packageName: String) {
        getPrefs(context).edit().putString(KEY_CURRENT_FOREGROUND_APP, packageName).apply()
    }

    fun getCurrentForegroundApp(context: Context): String {
        return getPrefs(context).getString(KEY_CURRENT_FOREGROUND_APP, "") ?: ""
    }

    fun getCurrentLogicDayKey(): Long {
        return LocalDateTime.now(ZoneId.systemDefault())
            .minusHours(6)
            .toLocalDate()
            .toEpochDay()
    }

    fun checkAndPerformDailyReset(context: Context) {
        val currentLogicDay = getCurrentLogicDayKey()
        val lastResetDay = getPrefs(context).getLong(KEY_LAST_RESET_DAY_KEY, Long.MIN_VALUE)

        if (currentLogicDay > lastResetDay) {
            val baseSeconds = getDailyBaseMinutes(context) * 60
            val updated = baseSeconds.coerceAtLeast(0)
            getPrefs(context).edit().putInt(KEY_BALANCE_SECONDS, updated).apply()
            syncDailyAccount(
                context = context,
                dayKey = currentLogicDay,
                baseDelta = baseSeconds,
                remainingSeconds = updated
            )
            getPrefs(context).edit().putLong(KEY_LAST_RESET_DAY_KEY, currentLogicDay).apply()
        }
    }

    private fun syncDailyAccount(
        context: Context,
        dayKey: Long,
        baseDelta: Int = 0,
        earnedDelta: Int = 0,
        spentDelta: Int = 0,
        remainingSeconds: Int
    ) {
        if (baseDelta == 0 && earnedDelta == 0 && spentDelta == 0) return

        val appContext = context.applicationContext
        dbExecutor.execute {
            runBlocking {
                val dao = AppDatabase.getDatabase(appContext).taskDao()
                val current = dao.getDailyAccountByDay(dayKey) ?: DailyAccount(logicalDayKey = dayKey)
                dao.upsertDailyAccount(
                    current.copy(
                        baseSecondsGranted = (current.baseSecondsGranted + baseDelta).coerceAtLeast(0),
                        earnedSeconds = (current.earnedSeconds + earnedDelta).coerceAtLeast(0),
                        spentSeconds = (current.spentSeconds + spentDelta).coerceAtLeast(0),
                        remainingSeconds = remainingSeconds.coerceAtLeast(0)
                    )
                )
            }
        }
    }
}