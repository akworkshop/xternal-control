package com.xternal.control.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*

class PlaystoreBillingManager(private val appContext: Context) : BillingManager, PurchasesUpdatedListener {

    private val tag = "BillingManager"
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(BillingManager.PREFS_NAME, Context.MODE_PRIVATE)

    private var billingClient: BillingClient? = null
    private var proProductDetails: ProductDetails? = null
    private var statusCallback: ((Boolean) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun initialize(onProStatusChanged: ((Boolean) -> Unit)?) {
        statusCallback = onProStatusChanged

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        connectToGooglePlay()
    }

    private fun connectToGooglePlay(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(tag, "Billing client connected successfully.")
                    queryProductDetails()
                    queryActivePurchases()
                    onConnected?.invoke()
                } else {
                    Log.w(tag, "Billing setup failed with code: ${billingResult.responseCode}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(tag, "Billing service disconnected. Will reconnect on next request.")
            }
        })
    }

    private fun queryProductDetails() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingManager.PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                proProductDetails = productDetailsList.firstOrNull {
                    it.productId == BillingManager.PRODUCT_ID_PRO
                }
                Log.d(tag, "Product details loaded: ${proProductDetails?.name}")
            }
        }
    }

    private fun queryActivePurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasPro = false
                for (purchase in purchases) {
                    if (purchase.products.contains(BillingManager.PRODUCT_ID_PRO) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        hasPro = true
                        handleAcknowledgeIfNeeded(purchase)
                    }
                }
                updateProStatus(hasPro)
            }
        }
    }

    override fun isProActive(): Boolean {
        return prefs.getBoolean(BillingManager.KEY_IS_PRO, false)
    }

    override fun purchasePro(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToGooglePlay {
                launchFlow(activity)
            }
        } else {
            launchFlow(activity)
        }
    }

    private fun launchFlow(activity: Activity) {
        val details = proProductDetails
        if (details == null) {
            // Try querying again
            queryProductDetails()
            Toast.makeText(
                activity,
                "Connecting to Google Play Store... Please try again in a moment.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    override fun restorePurchases(activity: Activity?) {
        connectToGooglePlay {
            queryActivePurchases()
            activity?.let { act ->
                mainHandler.post {
                    if (isProActive()) {
                        Toast.makeText(act, "Pro license restored successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(act, "No previous Pro purchase found for this Google account.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(BillingManager.PRODUCT_ID_PRO) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                ) {
                    handleAcknowledgeIfNeeded(purchase)
                    updateProStatus(true)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(tag, "User canceled the purchase.")
        } else {
            Log.w(tag, "Purchase failed: ${billingResult.debugMessage} (code: ${billingResult.responseCode})")
        }
    }

    private fun handleAcknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(tag, "Purchase acknowledged successfully.")
                }
            }
        }
    }

    private fun updateProStatus(active: Boolean) {
        prefs.edit().putBoolean(BillingManager.KEY_IS_PRO, active).apply()
        mainHandler.post {
            statusCallback?.invoke(active)
        }
    }

    override fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
