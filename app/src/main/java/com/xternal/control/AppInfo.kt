package com.xternal.control

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val banner: Drawable? = null,
    var dominantColor: Int? = null,
    var isFavourite: Boolean = false,
    var isLocked: Boolean = false
)
