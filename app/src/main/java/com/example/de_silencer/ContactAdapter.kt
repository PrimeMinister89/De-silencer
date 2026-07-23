package com.example.de_silencer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private var contactList: MutableList<ContactItem>,
                     private val onStatusChanged: (ContactItem, Boolean, ) -> Unit,
                     private val onItemClick: (ContactItem) -> Unit,
                     private val onItemLongClick: (ContactItem, View) -> Unit) :
    RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvContactName)
        val cbMonitor: CheckBox = itemView.findViewById(R.id.cbMonitor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val currentItem = contactList[position]
        val contact = contactList[position]

        holder.tvName.text = currentItem.name
        holder.cbMonitor.setOnCheckedChangeListener(null)

        holder.cbMonitor.isChecked = currentItem.isMonitored

        holder.cbMonitor.setOnCheckedChangeListener { _, isChecked ->
            currentItem.isMonitored = isChecked

            onStatusChanged(currentItem, isChecked)
        }

        holder.itemView.setOnClickListener {
            onItemClick(contact)
        }

        // 2. 设置长按监听
        holder.itemView.setOnLongClickListener { view ->
            onItemLongClick(contact, view)
            true // 返回 true 表示我们已经消费了这个事件，不会再触发普通点击
        }
    }

    fun addNewContactAtTop(newContact: ContactItem) {
        contactList.add(0, newContact)
        notifyItemInserted(0)
    }

    override fun getItemCount(): Int {
        return contactList.size
    }

    fun updateData(newList: List<ContactItem>) {
        // 1. 清空旧数据
        contactList.clear()
        // 2. 装入新合并的数据
        contactList.addAll(newList)
        // 3. 通知 RecyclerView重新渲染页面！
        notifyDataSetChanged()
    }

    fun updateItem(oldId: String, newItem: ContactItem) {
        val index = contactList.indexOfFirst { it.id == oldId }
        if (index != -1) {
            contactList[index] = newItem
            notifyItemChanged(index)
        }
    }

    // 3. 增加一个移除单条数据的方法
    fun removeItem(contact: ContactItem) {
        val index = contactList.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contactList.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}