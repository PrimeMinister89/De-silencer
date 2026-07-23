package com.example.de_silencer // 核对你的包名

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.PopupMenu

class PhoneFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContactAdapter
    private val REQUEST_CODE_WRITE_CONTACTS = 101
    private var pendingSyncName: String? = null
    private var pendingSyncPhone: String? = null

    // 1. 相当于 Activity 的 setContentView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_phone, container, false)
    }

    // 2. 界面加载完毕后，开始绑定控件和写逻辑
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewPhone)
        val fabAdd = view.findViewById<FloatingActionButton>(R.id.fabAddPhone)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ContactAdapter(
            mutableListOf(),
            onStatusChanged = { contact, isMonitored -> updateContactStatus(contact, isMonitored) },
            onItemClick = { contact -> showDetailDialog(contact) },
            onItemLongClick = { contact, view -> showDeleteMenu(contact, view) } // <- 处理长按
        )

        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            showAddContactDialog()
        }

        loadRealContacts()
    }

    private fun loadRealContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val finalContactList = mutableListOf<ContactItem>()
            val db = AppDatabase.getDatabase(requireContext())
            val monitoredList = withContext(Dispatchers.IO) {
                db.contactDao().getAllMonitored()
            }
            val existNumbers = mutableSetOf<String>()

            for (monitored in monitoredList) {
                val cleanNum = monitored.phoneNumber.replace(" ", "").removePrefix("+86")
                existNumbers.add(cleanNum)
                finalContactList.add(ContactItem("room_${cleanNum}", monitored.name, monitored.phoneNumber, true))
            }

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )

            requireActivity().contentResolver.query(uri, projection, null, null, null)?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "未知姓名"
                    val number = it.getString(numberIndex) ?: ""
                    val id = it.getString(idIndex) ?: ""
                    val cleanSystemNum = number.replace(" ", "").removePrefix("+86")

                    if (!existNumbers.contains(cleanSystemNum)) {
                        finalContactList.add(ContactItem(id, name, number, false))
                        existNumbers.add(cleanSystemNum)
                    }
                }
            }

            adapter.updateData(finalContactList)
        }
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etNewName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etNewPhone)

        AlertDialog.Builder(requireContext())
            .setTitle("添加白名单号码")
            .setView(dialogView)
            .setPositiveButton("完成") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    saveToAppDatabase(name, phone)
                    showSyncAskDialog(name, phone)
                } else {
                    Toast.makeText(requireContext(), "姓名或号码不能为空哦", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveToAppDatabase(name: String, phone: String) {
        val db = AppDatabase.getDatabase(requireContext())
        val monitoredContact = MonitoredContact(phone, name)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            db.contactDao().insert(monitoredContact)
            withContext(Dispatchers.Main) {
                val newItem = ContactItem(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name,
                    number = phone,
                    isMonitored = true
                )

                val adapter = recyclerView.adapter as? ContactAdapter
                adapter?.addNewContactAtTop(newItem)

                recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun updateContactStatus(contact: ContactItem, isMonitored: Boolean) {
        val db = AppDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (isMonitored) {
                db.contactDao().insert(MonitoredContact(contact.number, contact.name))
            } else {
                db.contactDao().delete(MonitoredContact(contact.number, contact.name))
            }
        }
    }

    private fun showSyncAskDialog(name: String, phone: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("同步到系统通讯录？")
            .setMessage("已成功加入 De-silencer 监控名单。是否顺便将 [$name] 添加到手机系统的通讯录中？")
            .setPositiveButton("是") { _, _ ->
                checkWritePermissionAndSync(name, phone)
            }
            .setNegativeButton("否", null)
            .show()
    }

    private fun checkWritePermissionAndSync(name: String, phone: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingSyncName = name
            pendingSyncPhone = phone
            // Fragment 中申请权限直接调用 requestPermissions
            requestPermissions(arrayOf(Manifest.permission.WRITE_CONTACTS), REQUEST_CODE_WRITE_CONTACTS)
        } else {
            syncToSystemContacts(name, phone)
        }
    }

    private fun syncToSystemContacts(name: String, phone: String) {
        val ops = ArrayList<ContentProviderOperation>()
        val rawContactInsertIndex = ops.size
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build())
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build())

        try {
            requireActivity().contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(requireContext(), "已同步至系统通讯录", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "同步失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    // 处理权限回调
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_WRITE_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val name = pendingSyncName
                val phone = pendingSyncPhone
                if (name != null && phone != null) {
                    syncToSystemContacts(name, phone)

                    pendingSyncName = null
                    pendingSyncPhone = null
                }
            } else {
                Toast.makeText(requireContext(), "没有通讯录写入权限，无法同步", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDetailDialog(contact: ContactItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_contact_detail, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDetailName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDetailPhone)

        // 初始化数据
        etName.setText(contact.name)
        etPhone.setText(contact.number)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("确认") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()

                if (newName.isNotEmpty() && newPhone.isNotEmpty()) {
                    performUpdate(contact, newName, newPhone)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performUpdate(oldContact: ContactItem, newName: String, newPhone: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            db.contactDao().delete(MonitoredContact(oldContact.number, oldContact.name))
            db.contactDao().insert(MonitoredContact(newPhone, newName))

            withContext(Dispatchers.Main) {
                // 更新 UI 列表
                val newItem = oldContact.copy(name = newName, number = newPhone, isMonitored = true)
                adapter.updateItem(oldContact.id, newItem)
                Toast.makeText(requireContext(), "修改成功", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 实现弹出菜单逻辑
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

    private fun performDelete(contact: ContactItem) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            // 从数据库中删除
            db.contactDao().delete(MonitoredContact(contact.number, contact.name))

            withContext(Dispatchers.Main) {
                // 从 UI 列表中移除
                adapter.removeItem(contact)
                Toast.makeText(requireContext(), "已移除：${contact.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}