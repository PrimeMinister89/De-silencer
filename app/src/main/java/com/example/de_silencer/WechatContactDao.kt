package com.example.de_silencer

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WechatContactDao {
    // 插入微信联系人
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wechatContact: WechatContact)

    // 删除微信联系人
    @Delete
    suspend fun delete(wechatContact: WechatContact)

    // 更新监控状态
    @Update
    suspend fun update(wechatContact: WechatContact)

    // 获取所有的微信监控列表
    @Query("SELECT * FROM wechat_contacts")
    suspend fun getAllWechatContacts(): List<WechatContact>

    // 获取所有处于“已监控”状态的微信联系人
    @Query("SELECT * FROM wechat_contacts WHERE isMonitored = 1")
    suspend fun getAllMonitoredWechat(): List<WechatContact>
}