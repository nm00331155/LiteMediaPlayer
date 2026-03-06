package com.example.litemediaplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lock_configs",
    indices = [Index(value = ["targetType", "targetId"], unique = true)]
)
data class LockConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,
    val targetId: Long?,
    val authMethod: String = "PIN",
    val pinHash: String? = null,
    val patternHash: String? = null,
    val autoLockMinutes: Int = 5,
    val isHidden: Boolean = false,
    val isEnabled: Boolean = true
)
