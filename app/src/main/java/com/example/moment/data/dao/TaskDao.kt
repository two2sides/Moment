package com.example.moment.data.dao

import androidx.room.*
import com.example.moment.data.entity.CompletedTask
import com.example.moment.data.entity.DailyAccount
import com.example.moment.data.entity.Task
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE

data class TaskTypeStat(
    val taskTypeSnapshot: String,
    val taskCount: Int,
    val rewardMinutes: Int
)

data class TopTaskStat(
    val taskTitleSnapshot: String,
    val completeCount: Int,
    val rewardMinutes: Int
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    suspend fun getActiveTasks(): List<Task>

    @Query(
        "SELECT * FROM tasks WHERE taskType = :appTaskType AND targetPackage IS NOT NULL"
    )
    suspend fun getTimeTrackingTasks(
        appTaskType: String = TASK_TYPE_APP_USAGE
    ): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Int)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedTask(completedTask: CompletedTask): Long

    @Query("DELETE FROM completed_records WHERE id = :recordId")
    suspend fun deleteCompletedTaskById(recordId: Int)

    @Query("SELECT COUNT(*) FROM completed_records WHERE logicalDayKey = :dayKey")
    suspend fun getCompletedCountByDay(dayKey: Long): Int

    @Query("SELECT COUNT(*) FROM completed_records WHERE logicalDayKey BETWEEN :startDayKey AND :endDayKey")
    suspend fun getCompletedCountByDayRange(startDayKey: Long, endDayKey: Long): Int

    @Query(
        "SELECT taskTypeSnapshot, COUNT(*) AS taskCount, COALESCE(SUM(rewardGrantedMinutes), 0) AS rewardMinutes " +
            "FROM completed_records " +
            "WHERE logicalDayKey BETWEEN :startDayKey AND :endDayKey " +
            "GROUP BY taskTypeSnapshot"
    )
    suspend fun getTaskTypeStatsByDayRange(startDayKey: Long, endDayKey: Long): List<TaskTypeStat>

    @Query(
        "SELECT taskTitleSnapshot, COUNT(*) AS completeCount, COALESCE(SUM(rewardGrantedMinutes), 0) AS rewardMinutes " +
            "FROM completed_records " +
            "WHERE logicalDayKey BETWEEN :startDayKey AND :endDayKey " +
            "GROUP BY taskTitleSnapshot " +
            "ORDER BY rewardMinutes DESC, completeCount DESC " +
            "LIMIT :limit"
    )
    suspend fun getTopTaskStatsByDayRange(startDayKey: Long, endDayKey: Long, limit: Int): List<TopTaskStat>

    @Query("SELECT DISTINCT logicalDayKey FROM completed_records ORDER BY logicalDayKey DESC")
    suspend fun getDistinctCompletedDayKeysDesc(): List<Long>

    @Query(
        "SELECT * FROM completed_records " +
            "WHERE (:titleKeyword = '' OR taskTitleSnapshot LIKE '%' || :titleKeyword || '%') " +
            "AND (:contentKeyword = '' OR submittedMarkdown LIKE '%' || :contentKeyword || '%' OR submittedText LIKE '%' || :contentKeyword || '%') " +
            "AND (:isRepeatable IS NULL OR isRepeatableSnapshot = :isRepeatable) " +
            "AND (:completedStartAt IS NULL OR completedAt >= :completedStartAt) " +
            "AND (:completedEndAt IS NULL OR completedAt <= :completedEndAt) " +
            "ORDER BY completedAt DESC"
    )
    suspend fun searchCompletedTasks(
        titleKeyword: String,
        contentKeyword: String,
        isRepeatable: Boolean?,
        completedStartAt: Long?,
        completedEndAt: Long?
    ): List<CompletedTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyAccount(dailyAccount: DailyAccount)

    @Query("SELECT * FROM daily_account WHERE logicalDayKey = :dayKey LIMIT 1")
    suspend fun getDailyAccountByDay(dayKey: Long): DailyAccount?


}
