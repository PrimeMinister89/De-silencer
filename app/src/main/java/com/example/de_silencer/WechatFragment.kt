package com.example.de_silencer // 核对你的包名

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.PopupMenu

class WechatFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // 加载刚才画的布局
        return inflater.inflate(R.layout.fragment_wechat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewWechat)
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fabAddWechat)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ContactAdapter(
            mutableListOf(),
            onStatusChanged = { contact, isMonitored ->
                updateWechatStatus(contact.name, isMonitored)
            },
            onItemClick = { contact ->
                // 绑定点击事件，弹出修改窗口
                showDetailDialog(contact)
            },
            onItemLongClick = { contact, v ->
                showDeleteMenu(contact, v)
            }
        )
        recyclerView.adapter = adapter

        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            showAddWechatDialog()
        }

        // 每次进入页面，加载微信监控数据
        loadWechatContacts()
    }

    private fun loadWechatContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            // 去 Room 数据库里把微信表的所有数据查出来
            val wechatList = withContext(Dispatchers.IO) {
                db.wechatContactDao().getAllWechatContacts()
            }

            // 把微信数据转换成 Adapter 认识的格式
            val displayList = wechatList.map {
                ContactItem(
                    id = "wx_${it.wechatName}", // 随便造个假 ID
                    name = it.wechatName,
                    number = "微信语音通话",
                    isMonitored = it.isMonitored
                )
            }
            // 刷新列表
            adapter.updateData(displayList)
        }
    }

    private fun showAddWechatDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_wechat, null)
        val etName = dialogView.findViewById<EditText>(R.id.etWechatName)

        AlertDialog.Builder(requireContext())
            .setTitle("添加微信监控")
            .setView(dialogView)
            .setPositiveButton("完成") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveWechatContact(name)
                } else {
                    Toast.makeText(requireContext(), "微信备注名不能为空哦", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveWechatContact(name: String) {
        val db = AppDatabase.getDatabase(requireContext())
        val newContact = WechatContact(wechatName = name, isMonitored = true)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // 存入 Room 数据库
            db.wechatContactDao().insert(newContact)

            withContext(Dispatchers.Main) {
                // UI 层面：极其丝滑地插到列表最上方
                val newItem = ContactItem("wx_$name", name, "微信语音通话", true)
                adapter.addNewContactAtTop(newItem)
                recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun updateWechatStatus(name: String, isMonitored: Boolean) {
        val db = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // 因为在 WechatContactDao 中我们写了 @Update，这里只需传个对象进去即可
            val contact = WechatContact(wechatName = name, isMonitored = isMonitored)
            db.wechatContactDao().update(contact)
        }
    }

    private fun showDetailDialog(contact: ContactItem) {
        // 复用微信添加弹窗的布局
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_wechat, null)
        val etName = dialogView.findViewById<EditText>(R.id.etWechatName)

        // 回显当前的微信备注名
        etName.setText(contact.name)

        AlertDialog.Builder(requireContext())
            .setTitle("修改微信备注")
            .setView(dialogView)
            .setPositiveButton("确认") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (newName != contact.name) {
                        performUpdate(contact, newName)
                    }
                } else {
                    Toast.makeText(requireContext(), "备注名不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performUpdate(oldContact: ContactItem, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            // 由于 wechatName 是主键，修改名字等同于更换主键
            // 1. 删除旧的备注名记录
            db.wechatContactDao().delete(WechatContact(wechatName = oldContact.name))
            // 2. 插入新的备注名记录（默认保持监控开启状态）
            db.wechatContactDao().insert(WechatContact(wechatName = newName, isMonitored = true))

            withContext(Dispatchers.Main) {
                // 3. 更新 UI 列表，同时更新 ID 以保持一致性
                val newItem = oldContact.copy(id = "wx_$newName", name = newName, isMonitored = true)
                adapter.updateItem(oldContact.id, newItem)
                Toast.makeText(requireContext(), "备注已更新为: $newName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performDelete(contact: ContactItem) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            // 删除微信白名单记录
            db.wechatContactDao().delete(WechatContact(wechatName = contact.name))

            withContext(Dispatchers.Main) {
                adapter.removeItem(contact)
            }
        }
    }

    private fun showDeleteMenu(contact: ContactItem, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menu.add("删除")
        popup.setOnMenuItemClickListener {
            if (it.title == "删除") {
                performDelete(contact)
            }
            true
        }
        popup.show()
    }
}