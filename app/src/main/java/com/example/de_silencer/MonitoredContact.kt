package com.example.de_silencer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_contacts")
data class MonitoredContact(
    @PrimaryKey val phoneNumber: String,
    val name: String
)