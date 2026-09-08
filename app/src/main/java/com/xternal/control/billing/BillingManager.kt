package com.xternal.control.billing

import android.app.Activity

interface BillingManager {
    fun initialize(onProStatusChanged: ((Boolean) -> Unit)? = null)
    fun isProActive(): Boolean
    fun purchasePro(activity: Activity)
    fun restorePurchases(activity: Activity? = null)
    fun destroy()

    companion object {
        const val PRODUCT_ID_PRO = "xternal_pro_lifetime"
        const val PREFS_NAME = "XternalControlPrefs"
        const val KEY_IS_PRO = "is_pro_active"
        const val FREE_MAX_FAVOURITES = 3
        const val FREE_MAX_APPS = 5
    }
}
