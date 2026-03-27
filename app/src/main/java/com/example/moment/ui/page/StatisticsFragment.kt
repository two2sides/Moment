package com.example.moment.ui.page

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.moment.R
import com.example.moment.data.AppDatabase
import com.example.moment.data.TimeManager
import com.example.moment.data.dao.TaskTypeStat
import com.example.moment.data.dao.TopTaskStat
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StatisticsUiModel(
    val currentBalanceMinutes: Int,
    val todayEarnedMinutes: Int,
    val todaySpentMinutes: Int,
    val todayNetMinutes: Int,
    val todayCompletedCount: Int,
    val weekCompletedCount: Int,
    val currentStreakDays: Int,
    val typeStats: List<TaskTypeStat>,
    val topTasks: List<TopTaskStat>
)

class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var tvCurrentBalance: TextView
    private lateinit var tvTodayEarned: TextView
    private lateinit var tvTodaySpent: TextView
    private lateinit var tvTodayNet: TextView
    private lateinit var tvTodayCompleted: TextView
    private lateinit var tvWeekCompleted: TextView
    private lateinit var tvStreak: TextView
    private lateinit var tvTypeBreakdown: TextView
    private lateinit var tvTopTasks: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCurrentBalance = view.findViewById(R.id.tvStatsCurrentBalance)
        tvTodayEarned = view.findViewById(R.id.tvStatsTodayEarned)
        tvTodaySpent = view.findViewById(R.id.tvStatsTodaySpent)
        tvTodayNet = view.findViewById(R.id.tvStatsTodayNet)
        tvTodayCompleted = view.findViewById(R.id.tvStatsTodayCompleted)
        tvWeekCompleted = view.findViewById(R.id.tvStatsWeekCompleted)
        tvStreak = view.findViewById(R.id.tvStatsStreak)
        tvTypeBreakdown = view.findViewById(R.id.tvStatsTypeBreakdown)
        tvTopTasks = view.findViewById(R.id.tvStatsTopTasks)

        loadStatistics()
    }

    override fun onResume() {
        super.onResume()
        loadStatistics()
    }

    private fun loadStatistics() {
        ioScope.launch {
            val context = requireContext()
            val dao = AppDatabase.getDatabase(context).taskDao()
            val currentDayKey = TimeManager.getCurrentLogicDayKey()
            val startDayKey = currentDayKey - 6

            val account = dao.getDailyAccountByDay(currentDayKey)
            val todayCompleted = dao.getCompletedCountByDay(currentDayKey)
            val weekCompleted = dao.getCompletedCountByDayRange(startDayKey, currentDayKey)
            val typeStats = dao.getTaskTypeStatsByDayRange(startDayKey, currentDayKey)
            val topTasks = dao.getTopTaskStatsByDayRange(startDayKey, currentDayKey, 5)
            val dayKeys = dao.getDistinctCompletedDayKeysDesc()

            val totalEarnedSeconds = (account?.baseSecondsGranted ?: 0) + (account?.earnedSeconds ?: 0)
            val spentSeconds = account?.spentSeconds ?: 0
            val model = StatisticsUiModel(
                currentBalanceMinutes = TimeManager.getBalanceSeconds(context) / 60,
                todayEarnedMinutes = totalEarnedSeconds / 60,
                todaySpentMinutes = spentSeconds / 60,
                todayNetMinutes = (totalEarnedSeconds - spentSeconds) / 60,
                todayCompletedCount = todayCompleted,
                weekCompletedCount = weekCompleted,
                currentStreakDays = calculateCurrentStreak(dayKeys, currentDayKey),
                typeStats = typeStats,
                topTasks = topTasks
            )

            activity?.runOnUiThread {
                render(model)
            }
        }
    }

    private fun render(model: StatisticsUiModel) {
        tvCurrentBalance.text = getString(R.string.stats_current_balance, model.currentBalanceMinutes)
        tvTodayEarned.text = getString(R.string.stats_today_earned, model.todayEarnedMinutes)
        tvTodaySpent.text = getString(R.string.stats_today_spent, model.todaySpentMinutes)
        tvTodayNet.text = getString(R.string.stats_today_net, model.todayNetMinutes)
        tvTodayCompleted.text = getString(R.string.stats_today_completed, model.todayCompletedCount)
        tvWeekCompleted.text = getString(R.string.stats_week_completed, model.weekCompletedCount)
        tvStreak.text = getString(R.string.stats_current_streak, model.currentStreakDays)

        val totalTypeCount = model.typeStats.sumOf { it.taskCount }
        if (totalTypeCount <= 0) {
            tvTypeBreakdown.text = getString(R.string.stats_type_empty)
        } else {
            tvTypeBreakdown.text = model.typeStats.joinToString(separator = "\n") { stat ->
                val label = if (stat.taskTypeSnapshot == TASK_TYPE_APP_USAGE) {
                    getString(R.string.task_type_app_usage)
                } else {
                    getString(R.string.task_type_manual_submit)
                }
                val percent = stat.taskCount * 100f / totalTypeCount
                getString(R.string.stats_type_item, label, stat.taskCount, stat.rewardMinutes, percent)
            }
        }

        if (model.topTasks.isEmpty()) {
            tvTopTasks.text = getString(R.string.stats_top_empty)
        } else {
            tvTopTasks.text = model.topTasks.mapIndexed { index, stat ->
                getString(
                    R.string.stats_top_item,
                    index + 1,
                    stat.taskTitleSnapshot,
                    stat.completeCount,
                    stat.rewardMinutes
                )
            }.joinToString(separator = "\n")
        }
    }

    private fun calculateCurrentStreak(sortedDayKeysDesc: List<Long>, currentDayKey: Long): Int {
        if (sortedDayKeysDesc.isEmpty()) return 0

        var expected = currentDayKey
        var streak = 0
        val unique = sortedDayKeysDesc.distinct()

        for (key in unique) {
            if (key == expected) {
                streak += 1
                expected -= 1
            } else if (key < expected) {
                break
            }
        }
        return streak
    }
}

