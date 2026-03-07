package com.example.de_silencer

import androidx.room.*

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: MonitoredContact)

    @Delete
    suspend fun delete(contact: MonitoredContact)

    @Query("SELECT * FROM monitored_contacts")
    suspend fun getAllMonitored(): List<MonitoredContact>
}