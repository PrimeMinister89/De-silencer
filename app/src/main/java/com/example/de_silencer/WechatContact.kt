package com.example.de_silencer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wechat_contacts")
data class WechatContact(
    @PrimaryKey val wechatName: String,
    val isMonitored: Boolean = false
)