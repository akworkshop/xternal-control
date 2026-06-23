package com.xternal.control

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    var isFavourite: Boolean = false
)
