package com.example.moment.service

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.example.moment.data.AppDatabase
import com.example.moment.data.TimeManager
import com.example.moment.data.entity.CompletedTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class AppMonitorService : AccessibilityService() {

    private companion object {
        const val TICK_INTERVAL_MS = 1000L
        const val USAGE_ACCOUNTING_INTERVAL_MS = 5000L
        const val USAGE_QUERY_GUARD_MS = 60000L
        const val KICK_COOLDOWN_MS = 1000L
    }

    private var currentForegroundApp = ""
    private var isTimerRunning = false
    private var lastUsageAccountingElapsedMs = 0L
    private var lastUsageProcessWallTimeMs = 0L
    private var lastKnownUsageForegroundApp = ""
    private var lastKickElapsedMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val taskProcessMutex = Mutex()

    private data class UsageAccountingResult(
        val consumedSeconds: Int,
        val matchedTaskCount: Int,
        val taskProgressSeconds: Int,
        val rewardGrantedSeconds: Int,
        val reason: String
    )

    private data class SettleResult(
        val shouldDeleteTask: Boolean,
        val rewardGrantedSeconds: Int
    )

    private val timeTicker = object : Runnable {
        override fun run() {
            val context = this@AppMonitorService
            maybeProcessUsageAccounting(context)
            scheduleNextTick()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        val isTransient = isTransientSystemOverlayPackage(packageName)
        if (!isTransient && currentForegroundApp != packageName) {
            currentForegroundApp = packageName
            TimeManager.setCurrentForegroundApp(this, packageName)
        }

        maybeKickBlockedAppIfNeeded(this, packageName)

        if (!isTimerRunning) {
            isTimerRunning = true
            handler.post(timeTicker)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeTicker)
        isTimerRunning = false
        currentForegroundApp = ""
        lastUsageAccountingElapsedMs = 0L
        lastUsageProcessWallTimeMs = 0L
        lastKnownUsageForegroundApp = ""
        lastKickElapsedMs = 0L
        TimeManager.setCurrentForegroundApp(this, "")
    }

    private fun maybeKickBlockedAppIfNeeded(context: AppMonitorService, packageNameFromEvent: String) {
        val fg = normalizePackageName(packageNameFromEvent)
        if (fg.isBlank()) return
        if (isTransientSystemOverlayPackage(fg)) return

        val blocked = TimeManager.getBlockedApps(context)
            .asSequence()
            .map { normalizePackageName(it) }
            .toSet()
        if (!blocked.contains(fg)) return
        if (TimeManager.getBalanceSeconds(context) > 0) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastKickElapsedMs < KICK_COOLDOWN_MS) return

        lastKickElapsedMs = now
        kickToHome()
    }

    private fun maybeProcessUsageAccounting(context: AppMonitorService) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val shouldRun =
            lastUsageAccountingElapsedMs == 0L || nowElapsed - lastUsageAccountingElapsedMs >= USAGE_ACCOUNTING_INTERVAL_MS
        if (!shouldRun) return
        lastUsageAccountingElapsedMs = nowElapsed

        if (!hasUsageAccess()) {
            return
        }

        val endWallMs = System.currentTimeMillis()
        if (lastUsageProcessWallTimeMs == 0L) {
            lastUsageProcessWallTimeMs = endWallMs - USAGE_ACCOUNTING_INTERVAL_MS
            return
        }

        val startWallMs = max(lastUsageProcessWallTimeMs, endWallMs - USAGE_QUERY_GUARD_MS)
        if (endWallMs <= startWallMs) return

        val usageSlice = queryUsageSlice(startWallMs, endWallMs)
        lastUsageProcessWallTimeMs = endWallMs
        if (usageSlice.currentForegroundApp.isNotBlank()) {
            lastKnownUsageForegroundApp = usageSlice.currentForegroundApp
            TimeManager.setCurrentForegroundApp(this, usageSlice.currentForegroundApp)
        }

        serviceScope.launch {
            taskProcessMutex.withLock {
                applyUsageAccounting(context, usageSlice.packageDurationMs)
            }
        }
    }

    private suspend fun applyUsageAccounting(
        context: AppMonitorService,
        durationByPackageMs: Map<String, Long>
    ): UsageAccountingResult {
        if (durationByPackageMs.isEmpty()) {
            return UsageAccountingResult(0, 0, 0, 0, "NO_USAGE_EVENTS")
        }

        val durationByPackageSeconds = durationByPackageMs.mapValues { (_, ms) -> (ms / 1000L).toInt() }
            .filterValues { it > 0 }
        if (durationByPackageSeconds.isEmpty()) {
            return UsageAccountingResult(0, 0, 0, 0, "SLICE_LT_1S")
        }

        val blockedAppsNormalized = TimeManager.getBlockedApps(context)
            .map { normalizePackageName(it) }
            .toSet()
        val consumedSeconds = durationByPackageSeconds.entries
            .filter { blockedAppsNormalized.contains(normalizePackageName(it.key)) }
            .sumOf { it.value }
            .coerceAtLeast(0)
        if (consumedSeconds > 0) {
            TimeManager.consumeSeconds(context, consumedSeconds)
        }

        val dao = AppDatabase.getDatabase(context).taskDao()
        val tasks = dao.getTimeTrackingTasks()
        var matchedTaskCount = 0
        var taskProgressSeconds = 0
        var rewardGrantedSeconds = 0

        tasks.forEach { task ->
            val target = normalizePackageName(task.targetPackage)
            if (target.isBlank()) return@forEach
            val addSeconds = durationByPackageSeconds.entries
                .firstOrNull { normalizePackageName(it.key) == target }
                ?.value
                ?: 0
            if (addSeconds <= 0) return@forEach

            matchedTaskCount += 1
            taskProgressSeconds += addSeconds
            task.currentProgressSeconds += addSeconds

            val settleResult = settleTaskIfReachedTarget(context, dao, task)
            rewardGrantedSeconds += settleResult.rewardGrantedSeconds
            if (!settleResult.shouldDeleteTask) {
                dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
            }
        }

        val reason = when {
            matchedTaskCount > 0 -> "COUNTING"
            tasks.isEmpty() -> "NO_REWARD_TASKS"
            else -> "FOREGROUND_NOT_MATCHED"
        }
        return UsageAccountingResult(
            consumedSeconds = consumedSeconds,
            matchedTaskCount = matchedTaskCount,
            taskProgressSeconds = taskProgressSeconds,
            rewardGrantedSeconds = rewardGrantedSeconds,
            reason = reason
        )
    }

    private data class UsageSlice(
        val packageDurationMs: Map<String, Long>,
        val currentForegroundApp: String
    )

    private fun queryUsageSlice(startMs: Long, endMs: Long): UsageSlice {
        val usageStatsManager = getSystemService(UsageStatsManager::class.java)
            ?: return UsageSlice(emptyMap(), lastKnownUsageForegroundApp)

        val events = usageStatsManager.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        val durationMap = mutableMapOf<String, Long>()
        var activePkg = lastKnownUsageForegroundApp
        var cursor = startMs

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startMs, endMs)
            if (activePkg.isNotBlank() && ts > cursor) {
                durationMap[activePkg] = (durationMap[activePkg] ?: 0L) + (ts - cursor)
            }

            val pkg = event.packageName.orEmpty()
            when {
                isForegroundUsageEvent(event) && pkg.isNotBlank() && !isTransientSystemOverlayPackage(pkg) -> {
                    activePkg = pkg
                }
                isBackgroundUsageEvent(event) && pkg == activePkg -> {
                    activePkg = ""
                }
            }
            cursor = ts
        }

        if (activePkg.isNotBlank() && endMs > cursor) {
            durationMap[activePkg] = (durationMap[activePkg] ?: 0L) + (endMs - cursor)
        }

        return UsageSlice(durationMap, activePkg)
    }

    private suspend fun settleTaskIfReachedTarget(
        context: AppMonitorService,
        dao: com.example.moment.data.dao.TaskDao,
        task: com.example.moment.data.entity.Task
    ): SettleResult {
        if (task.targetSeconds <= 0 || task.currentProgressSeconds < task.targetSeconds) {
            return SettleResult(shouldDeleteTask = false, rewardGrantedSeconds = 0)
        }

        val settleCount = if (task.isRepeatable) task.currentProgressSeconds / task.targetSeconds else 1
        var rewardGrantedSeconds = 0
        if (settleCount > 0) {
            rewardGrantedSeconds = settleCount * task.rewardMinutes * 60
            TimeManager.addSeconds(context, rewardGrantedSeconds)
            repeat(settleCount) { dao.insertCompletedTask(buildCompletedTaskSnapshot(task)) }
        }

        if (task.isRepeatable) {
            task.currentProgressSeconds %= task.targetSeconds
            return SettleResult(shouldDeleteTask = false, rewardGrantedSeconds = rewardGrantedSeconds)
        }

        dao.deleteTask(task)
        return SettleResult(shouldDeleteTask = true, rewardGrantedSeconds = rewardGrantedSeconds)
    }

    private fun buildCompletedTaskSnapshot(task: com.example.moment.data.entity.Task): CompletedTask {
        return CompletedTask(
            taskId = task.id,
            taskTitleSnapshot = task.title,
            taskNoteSnapshot = task.note,
            taskTypeSnapshot = task.taskType,
            isRepeatableSnapshot = task.isRepeatable,
            rewardGrantedMinutes = task.rewardMinutes,
            taskCreatedAtSnapshot = task.createdAt,
            completedAt = System.currentTimeMillis(),
            logicalDayKey = TimeManager.getCurrentLogicDayKey()
        )
    }


    private fun hasUsageAccess(): Boolean {
        val appOpsManager = getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isForegroundUsageEvent(event: UsageEvents.Event): Boolean {
        return event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
    }

    private fun isBackgroundUsageEvent(event: UsageEvents.Event): Boolean {
        return event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_STOPPED))
    }

    private fun kickToHome() {
        val acted = performGlobalAction(GLOBAL_ACTION_HOME)
        if (!acted) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
    }

    private fun scheduleNextTick() {
        handler.postDelayed(timeTicker, TICK_INTERVAL_MS)
    }

    private fun normalizePackageName(packageName: String?): String {
        return packageName?.trim()?.lowercase().orEmpty()
    }

    private fun isTransientSystemOverlayPackage(packageName: String): Boolean {
        return packageName == "android" ||
            packageName.contains("systemui", ignoreCase = true) ||
            packageName.contains("launcher", ignoreCase = true) ||
            packageName == "com.miui.home"
    }
}