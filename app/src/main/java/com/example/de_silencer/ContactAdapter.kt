package com.example.de_silencer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private var contactList: MutableList<ContactItem>,
                     private val onStatusChanged: (ContactItem, Boolean) -> Unit) :
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

        holder.tvName.text = currentItem.name
        holder.cbMonitor.setOnCheckedChangeListener(null)

        holder.cbMonitor.isChecked = currentItem.isMonitored

        holder.cbMonitor.setOnCheckedChangeListener { _, isChecked ->
            currentItem.isMonitored = isChecked

            onStatusChanged(currentItem, isChecked)
        }
    }

    fun addNewContactAtTop(newContact: ContactItem) {
        contactList.add(0, newContact)
        notifyItemInserted(0)
    }

    override fun getItemCount(): Int {
        return contactList.size
    }
}