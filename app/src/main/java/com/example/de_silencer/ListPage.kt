package com.example.de_silencer

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.ContactsContract
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentProviderOperation
import android.content.Intent
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.net.Uri
import android.provider.Settings




class ListPage : AppCompatActivity() {
    private val REQUEST_CODE_WRITE_CONTACTS = 101

    private var pendingSyncName: String? = null
    private var pendingSyncPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_list_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewContacts)

        recyclerView.layoutManager = LinearLayoutManager(this)

        checkPermission()

        val fabAddContact = findViewById<FloatingActionButton>(R.id.floatingActionButton3)
        fabAddContact.setOnClickListener {
            showAddContactDialog()
        }
    }

    private val REQUEST_CODE_CONTACTS = 100

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CODE_CONTACTS)
        } else {
            loadRealContacts()
        }
    }

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
                AlertDialog.Builder(this)
                    .setTitle("需要通讯录权限")
                    .setMessage("没有权限，De-silencer 无法将号码写入系统。如果需要该功能，请前往设置中手动开启“通讯录”权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    .setNegativeButton("算了", null)
                    .show()
            }
        }
    }

    private fun loadRealContacts() {
        lifecycleScope.launch {
            val finalContactList = mutableListOf<ContactItem>()

            val db = AppDatabase.getDatabase(this@ListPage)
            val monitoredList = withContext(Dispatchers.IO) {
                db.contactDao().getAllMonitored()
            }

            val existNumbers = mutableSetOf<String>()

            for (monitored in monitoredList) {
                val cleanNum = monitored.phoneNumber.replace(" ", "").removePrefix("+86")
                existNumbers.add(cleanNum)

                finalContactList.add(
                    ContactItem(
                        id = "room_${cleanNum}",
                        name = monitored.name,
                        number = monitored.phoneNumber,
                        isMonitored = true
                    )
                )
            }

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )

            val cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "未知姓名"
                    val number = it.getString(numberIndex) ?: ""
                    val id = it.getString(idIndex) ?: ""

                    val cleanSystemNum = number.replace(" ", "").removePrefix("+86")

                    if (!existNumbers.contains(cleanSystemNum)) {
                        finalContactList.add(
                            ContactItem(id, name, number, isMonitored = false)
                        )
                        existNumbers.add(cleanSystemNum)
                    }
                }
            }

            updateUI(finalContactList)
        }
    }

    private fun updateUI(contacts: MutableList<ContactItem>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewContacts)
        val adapter = ContactAdapter(contacts) { contact, isChecked ->
            val db = AppDatabase.getDatabase(this)
            val monitored = MonitoredContact(contact.number, contact.name)

            lifecycleScope.launch {
                if (isChecked) {
                    db.contactDao().insert(monitored)
                } else {
                    db.contactDao().delete(monitored)
                }
            }
        }
        recyclerView.adapter = adapter
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.etNewName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etNewPhone)

        AlertDialog.Builder(this)
            .setTitle("添加白名单号码")
            .setView(dialogView)
            .setPositiveButton("完成") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    saveToAppDatabase(name, phone)
                    showSyncAskDialog(name, phone)
                } else {
                    Toast.makeText(this, "姓名或号码不能为空哦", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveToAppDatabase(name: String, phone: String) {
        val db = AppDatabase.getDatabase(this)
        val monitoredContact = MonitoredContact(phone, name)

        lifecycleScope.launch(Dispatchers.IO) {
            db.contactDao().insert(monitoredContact)

            withContext(Dispatchers.Main) {
                val newItem = ContactItem(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name,
                    number = phone,
                    isMonitored = true
                )

                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewContacts)
                val adapter = recyclerView.adapter as? ContactAdapter
                adapter?.addNewContactAtTop(newItem)

                recyclerView.scrollToPosition(0)
            }
        }
    }

    private fun showSyncAskDialog(name: String, phone: String) {
        AlertDialog.Builder(this)
            .setTitle("是否需要同步到系统通讯录？")
            .setMessage("已成功加入 De-silencer 监控名单。是否顺便将 [$name] 添加到手机系统的通讯录中？")
            .setPositiveButton("是") { _, _ ->
                checkWritePermissionAndSync(name, phone)
            }
            .setNegativeButton("否", null)
            .show()
    }

    private fun syncToSystemContacts(name: String, phone: String) {
        val ops = ArrayList<ContentProviderOperation>()
        val rawContactInsertIndex = ops.size

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build())

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Toast.makeText(this, "已同步至系统通讯录", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "同步失败，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkWritePermissionAndSync(name: String, phone: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingSyncName = name
            pendingSyncPhone = phone

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_CONTACTS),
                REQUEST_CODE_WRITE_CONTACTS
            )
        } else {
            syncToSystemContacts(name, phone)
        }
    }
}