package com.example.de_silencer

data class ContactItem(
    val id : String,
    val name : String,
    val number : String,
    var isMonitored: Boolean = false
)
