package com.example.de_silencer

import android.app.Notification
import android.content.Context
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.PowerManager

class WechatNotificationListener : NotificationListenerService() {

    private lateinit var audioManager: AudioManager
    // 用来记录被我们强行修改前的静音状态，方便通话结束后恢复
    private var previousRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var isModeChangedByApp = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // 当系统收到任何新通知时，这个方法会被触发
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 1. 初级过滤：如果不是微信的通知，直接放行，不管它
        if (sbn.packageName != "com.tencent.mm") return

        val notification = sbn.notification
        val extras = notification.extras

        // 2. 提取通知的标题（通常是微信备注名）和内容
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getString(Notification.EXTRA_TEXT) ?: return

        // 打印日志方便我们在控制台调试
        Log.d("WechatListener", "收到微信通知 - 标题: $title, 内容: $text")

        // 3. 核心匹配：判断这到底是不是一个来电邀请
        if (text.contains("语音通话") || text.contains("视频通话") || text.contains("邀请你")) {
            handleIncomingWechatCall(title) // 标题就是我们要去数据库里查的名字！
        }
    }

    // 当通知被移除时（比如接听了、挂断了、或者漏接了），这个方法会被触发
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.tencent.mm") {
            // 如果之前是我们强行把手机弄响的，现在通话结束了，必须恢复原状
            if (isModeChangedByApp) {
                audioManager.ringerMode = previousRingerMode
                isModeChangedByApp = false
                Log.d("WechatListener", "微信通话结束，已恢复之前的静音状态")
            }
        }
    }

    // 处理解除静音和记录日志的逻辑
    private fun handleIncomingWechatCall(callerName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentMode = audioManager.ringerMode

            // 只有当手机本身处于静音或震动时才介入
            if (currentMode == AudioManager.RINGER_MODE_SILENT || currentMode == AudioManager.RINGER_MODE_VIBRATE) {
                val db = AppDatabase.getDatabase(applicationContext)

                // 去我们刚建好的微信白名单表里查！
                val monitoredWechatList = db.wechatContactDao().getAllMonitoredWechat()
                val isWhiteList = monitoredWechatList.any { it.wechatName == callerName }

                // 完美复用 V1.0 的日志系统，把微信拦截记录也写进去！
                val actionType = if (isWhiteList) 1 else 0
                val logEntry = CallLog(
                    callerName = callerName,
                    phoneNumber = "微信通话",
                    timestamp = System.currentTimeMillis(),
                    actionType = actionType
                )
                db.callLogDao().insertLog(logEntry)

                // 如果在白名单里，执行破冰魔法
                if (isWhiteList) {
                    withContext(Dispatchers.Main) {
                        previousRingerMode = currentMode
                        isModeChangedByApp = true
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        Log.d("WechatListener", "匹配到微信白名单: $callerName，已强制响铃！")
                    }
                }
            }
        }
    }
}