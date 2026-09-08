package com.xternal.control.billing

import android.content.Context

object BillingManagerProvider {
    @Volatile
    private var instance: BillingManager? = null

    fun getInstance(context: Context): BillingManager {
        return instance ?: synchronized(this) {
            instance ?: GithubBillingManager(context.applicationContext).also {
                instance = it
            }
        }
    }
}
