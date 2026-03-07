package com.example.de_silencer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter(private var logList: MutableList<CallLog>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvLogName)
        val tvTime: TextView = itemView.findViewById(R.id.tvLogTime)
        val tvAction: TextView = itemView.findViewById(R.id.tvLogAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logList[position]

        holder.tvName.text = "${log.callerName} (${log.phoneNumber})"

        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(log.timestamp))

        if (log.actionType == 1) {
            holder.tvAction.text = "[守护成功] 手机处于静音，已强制响铃"
            holder.tvAction.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            holder.tvAction.text = "[保持静音] 非白名单来电，未响铃"
            holder.tvAction.setTextColor(Color.parseColor("#888888"))
        }
    }

    override fun getItemCount(): Int = logList.size

    fun updateData(newList: List<CallLog>) {
        logList.clear()
        logList.addAll(newList)
        notifyDataSetChanged()
    }
}