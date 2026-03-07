package com.example.de_silencer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogActivity : AppCompatActivity() {

    private lateinit var adapter: LogAdapter
    private lateinit var tvEmptyLog: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvEmptyLog = findViewById(R.id.tvEmptyLog)
        recyclerView = findViewById(R.id.recyclerViewLogs)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LogAdapter(mutableListOf())
        recyclerView.adapter = adapter

        loadLogs()
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@LogActivity)
            val logs = withContext(Dispatchers.IO) {
                db.callLogDao().getAllLogs()
            }

            if (logs.isEmpty()) {
                tvEmptyLog.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvEmptyLog.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.updateData(logs)
            }
        }
    }
}