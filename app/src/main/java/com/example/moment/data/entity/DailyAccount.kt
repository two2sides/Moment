package com.example.moment.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_account")
data class DailyAccount(
    @PrimaryKey val logicalDayKey: Long,
    val baseSecondsGranted: Int = 0,
    val earnedSeconds: Int = 0,
    val spentSeconds: Int = 0,
    val remainingSeconds: Int = 0
)

