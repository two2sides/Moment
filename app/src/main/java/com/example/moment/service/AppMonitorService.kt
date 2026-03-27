package com.example.moment.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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

class AppMonitorService : AccessibilityService() {

    private companion object {
        const val TAG = "MomentService"
        const val TICK_INTERVAL_MS = 1000L
    }

    private var currentForegroundApp = ""
    private var isTimerRunning = false
    private var lastTickElapsedMs = 0L
    private var tickRemainderMs = 0L
    private val taskProcessMutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private val timeTicker = object : Runnable {
        override fun run() {
            val context = this@AppMonitorService

            val elapsedSeconds = calculateElapsedSecondsForTick()
            if (elapsedSeconds <= 0) {
                scheduleNextTick()
                return
            }

            val foregroundAppSnapshot = currentForegroundApp
            logTick(elapsedSeconds, foregroundAppSnapshot)
            consumeBlockedAppTimeIfNeeded(context, foregroundAppSnapshot, elapsedSeconds)
            processTrackingTasks(context, foregroundAppSnapshot, elapsedSeconds)
            scheduleNextTick()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // 关键过滤：忽略系统 UI 相关的包名，避免覆盖真正的应用包名
            if (packageName == "android" || 
                packageName.contains("systemui") || 
                packageName.contains("settings") ||
                packageName.contains("launcher")) {
                return
            }

            if (currentForegroundApp != packageName) {
                Log.d(TAG, "App Changed: $currentForegroundApp -> $packageName")
                currentForegroundApp = packageName
                TimeManager.setCurrentForegroundApp(this, packageName)
            }
            
            if (!isTimerRunning) {
                Log.d(TAG, "Starting Ticker...")
                isTimerRunning = true
                handler.post(timeTicker)
            }
        }
    }

    private fun kickToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeTicker)
        isTimerRunning = false
        lastTickElapsedMs = 0L
        tickRemainderMs = 0L
        TimeManager.setCurrentForegroundApp(this, "")
    }

    private fun calculateElapsedSecondsForTick(): Int {
        val now = SystemClock.elapsedRealtime()
        if (lastTickElapsedMs == 0L) {
            lastTickElapsedMs = now
            return 0
        }

        val deltaMs = now - lastTickElapsedMs
        lastTickElapsedMs = now
        tickRemainderMs += deltaMs
        val elapsedSeconds = (tickRemainderMs / 1000L).toInt()
        tickRemainderMs %= 1000L
        return elapsedSeconds
    }

    private fun logTick(elapsedSeconds: Int, foregroundApp: String) {
        Log.d(
            TAG,
            "Tick! Current App: $foregroundApp, elapsed=${elapsedSeconds}s, Balance: ${TimeManager.getBalanceSeconds(this)}"
        )
    }

    private fun consumeBlockedAppTimeIfNeeded(
        context: AppMonitorService,
        foregroundApp: String,
        elapsedSeconds: Int
    ) {
        val blockedApps = TimeManager.getBlockedApps(context)
        if (!blockedApps.contains(foregroundApp)) return

        val balance = TimeManager.getBalanceSeconds(context)
        if (balance > 0) {
            TimeManager.consumeSeconds(context, elapsedSeconds)
            if (TimeManager.getBalanceSeconds(context) <= 0) {
                Log.d(TAG, "Balance empty after consume! Kicking...")
                kickToHome()
            }
        } else {
            Log.d(TAG, "Balance empty! Kicking...")
            kickToHome()
        }
    }

    private fun processTrackingTasks(
        context: AppMonitorService,
        foregroundApp: String,
        elapsedSeconds: Int
    ) {
        serviceScope.launch {
            taskProcessMutex.withLock {
                val dao = AppDatabase.getDatabase(context).taskDao()
                val tasks = dao.getTimeTrackingTasks().filter { it.targetPackage == foregroundApp }

                tasks.forEach { task ->
                    task.currentProgressSeconds += elapsedSeconds
                    Log.d(TAG, "Tracking Task: ${task.title}, Progress: ${task.currentProgressSeconds}/${task.targetSeconds} (seconds)")

                    val shouldSkipUpdate = settleTaskIfReachedTarget(context, dao, task)
                    if (!shouldSkipUpdate) {
                        dao.updateTask(task.copy(updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    private suspend fun settleTaskIfReachedTarget(
        context: AppMonitorService,
        dao: com.example.moment.data.dao.TaskDao,
        task: com.example.moment.data.entity.Task
    ): Boolean {
        if (task.targetSeconds <= 0 || task.currentProgressSeconds < task.targetSeconds) {
            return false
        }

        val settleCount = if (task.isRepeatable) task.currentProgressSeconds / task.targetSeconds else 1
        if (settleCount > 0) {
            TimeManager.addSeconds(context, settleCount * task.rewardMinutes * 60)
            repeat(settleCount) { dao.insertCompletedTask(buildCompletedTaskSnapshot(task)) }
        }

        if (task.isRepeatable) {
            task.currentProgressSeconds %= task.targetSeconds
            return false
        }

        dao.deleteTask(task)
        return true
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

    private fun scheduleNextTick() {
        handler.postDelayed(timeTicker, TICK_INTERVAL_MS)
    }
}