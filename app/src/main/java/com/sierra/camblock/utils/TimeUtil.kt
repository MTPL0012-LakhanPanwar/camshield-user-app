package com.sierra.camblock.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getTimeFormat(context: Context, time: Long): String {
    val format = SimpleDateFormat("hh:mm a", Locale.US)
    return format.format(Date(time))
}

fun getTimeFormat24Hour(context: Context, time: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.US)
    return format.format(Date(time))
}
