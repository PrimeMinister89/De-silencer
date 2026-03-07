package com.example.de_silencer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val callerName: String,
    val phoneNumber: String,
    val timestamp: Long,
    val actionType: Int
)