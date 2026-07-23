package com.example.de_silencer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.PowerManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    companion object {
        var previousRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
        var isModeChangedByApp: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (incomingNumber != null) {
                        checkDatabaseAndUnmute(context, incomingNumber, audioManager)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (isModeChangedByApp) {
                        audioManager.ringerMode = previousRingerMode
                        isModeChangedByApp = false
                    }
                }
            }
        }
    }

    private fun checkDatabaseAndUnmute(context: Context, incomingNumber: String, audioManager: AudioManager) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentMode = audioManager.ringerMode

            if (currentMode == AudioManager.RINGER_MODE_SILENT || currentMode == AudioManager.RINGER_MODE_VIBRATE) {

                val cleanIncoming = incomingNumber.replace(" ", "").removePrefix("+86")

                val db = AppDatabase.getDatabase(context)
                val monitoredList = db.contactDao().getAllMonitored()

                val matchedContact = monitoredList.find {
                    val dbNumber = it.phoneNumber.replace(" ", "").removePrefix("+86")
                    dbNumber == cleanIncoming || dbNumber.contains(cleanIncoming) || cleanIncoming.contains(dbNumber)
                }

                val isWhiteList = matchedContact != null

                val callerName = matchedContact?.name ?: "未知/未监控号码"
                val actionType = if (isWhiteList) 1 else 0

                val logEntry = CallLog(
                    callerName = callerName,
                    phoneNumber = incomingNumber,
                    timestamp = System.currentTimeMillis(),
                    actionType = actionType
                )
                db.callLogDao().insertLog(logEntry)

                if (isWhiteList) {
                    withContext(Dispatchers.Main) {
                        previousRingerMode = currentMode
                        isModeChangedByApp = true
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    }
                }
            }
        }
    }
}