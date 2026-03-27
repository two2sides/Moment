package com.example.moment.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val note: String? = null,
    val taskType: String, // "APP_USAGE", "MANUAL_SUBMIT"
    val targetPackage: String? = null,
    val targetSeconds: Int = 0,
    val rewardMinutes: Int = 0,
    var currentProgressSeconds: Int = 0,
    val isRepeatable: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

const val TASK_TYPE_APP_USAGE = "APP_USAGE"
const val TASK_TYPE_MANUAL_SUBMIT = "MANUAL_SUBMIT"

