package com.example.moment.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completed_records",
    indices = [Index("completedAt")]
)
data class CompletedTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val taskTitleSnapshot: String,
    val taskNoteSnapshot: String? = null,
    val taskTypeSnapshot: String,
    val isRepeatableSnapshot: Boolean = false,
    val submittedMarkdown: String? = null,
    val submittedText: String? = null,
    val submittedImageUri: String? = null,
    val rewardGrantedMinutes: Int,
    val taskCreatedAtSnapshot: Long,
    val completedAt: Long,
    val logicalDayKey: Long
)
