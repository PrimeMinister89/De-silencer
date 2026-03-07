package com.example.de_silencer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CallLogDao {
    @Insert
    suspend fun insertLog(callLog: CallLog)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT 500")
    suspend fun getAllLogs(): List<CallLog>

    @Query("DELETE FROM call_logs")
    suspend fun clearAllLogs()
}