package com.xternal.control.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class GithubBillingManager(private val context: Context) : BillingManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun initialize(onProStatusChanged: ((Boolean) -> Unit)?) {
        mainHandler.post {
            onProStatusChanged?.invoke(true)
        }
    }

    override fun isProActive(): Boolean = true

    override fun purchasePro(activity: Activity) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/akworkshop"))
        activity.startActivity(intent)
    }

    override fun restorePurchases(activity: Activity?) {
        activity?.let {
            Toast.makeText(it, "GitHub release has all features permanently unlocked!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun destroy() {}
}
