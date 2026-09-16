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
    private val trialManager = PlaystoreTrialManager(appContext)

    override fun initialize(onProStatusChanged: ((Boolean) -> Unit)?) {
        statusCallback = onProStatusChanged

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        connectToGooglePlay(
            onConnected = {
                queryProductDetails()
                queryActivePurchases()
            }
        )
    }

    private fun connectToGooglePlay(onConnected: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val client = billingClient ?: run {
            onError?.invoke("Billing client is null")
            return
        }
        if (client.isReady) {
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(tag, "Billing client connected successfully.")
                    onConnected?.invoke()
                } else {
                    val msg = "Billing setup failed (${billingResult.responseCode}): ${billingResult.debugMessage}"
                    Log.w(tag, msg)
                    onError?.invoke(msg)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(tag, "Billing service disconnected. Will reconnect on next request.")
            }
        })
    }

    private fun queryProductDetails(onComplete: ((ProductDetails?, String?) -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToGooglePlay(
                onConnected = { queryProductDetails(onComplete) },
                onError = { err -> onComplete?.invoke(null, err) }
            )
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingManager.PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList
                val details = list.firstOrNull { it.productId == BillingManager.PRODUCT_ID_PRO }
                proProductDetails = details
                if (details != null) {
                    Log.d(tag, "Product details loaded: ${details.name} - ${details.oneTimePurchaseOfferDetails?.formattedPrice}")
                    onComplete?.invoke(details, null)
                } else {
                    val err = "Product '${BillingManager.PRODUCT_ID_PRO}' not found in Google Play (returned ${list.size} items). Verify that '${BillingManager.PRODUCT_ID_PRO}' is Active in Google Play Console."
                    Log.w(tag, err)
                    onComplete?.invoke(null, err)
                }
            } else {
                val err = "Google Play query failed (${billingResult.responseCode}): ${billingResult.debugMessage}"
                Log.e(tag, err)
                onComplete?.invoke(null, err)
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

    fun isPurchasedPro(): Boolean {
        return prefs.getBoolean(BillingManager.KEY_IS_PRO, false)
    }

    override fun isProActive(): Boolean {
        return isPurchasedPro() || trialManager.isTrialActive()
    }

    override fun isTrialActive(): Boolean = !isPurchasedPro() && trialManager.isTrialActive()
    override fun isTrialExpired(): Boolean = !isPurchasedPro() && trialManager.isTrialExpired()
    override fun getTrialHoursRemaining(): Int = if (isPurchasedPro()) 0 else trialManager.getTrialHoursRemaining()
    override fun shouldShowTrialExpiredDialog(): Boolean = !isPurchasedPro() && trialManager.shouldShowTrialExpiredDialog()
    override fun markTrialExpiredDialogShown() = trialManager.markTrialExpiredDialogShown()

    override fun purchasePro(activity: Activity) {
        val cachedDetails = proProductDetails
        if (cachedDetails != null) {
            launchFlow(activity, cachedDetails)
            return
        }

        Toast.makeText(activity, "Connecting to Google Play Store...", Toast.LENGTH_SHORT).show()
        queryProductDetails { details, error ->
            mainHandler.post {
                if (details != null) {
                    launchFlow(activity, details)
                } else {
                    Toast.makeText(
                        activity,
                        error ?: "Could not load product from Google Play. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun launchFlow(activity: Activity, details: ProductDetails) {
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
        Toast.makeText(activity ?: appContext, "Checking purchase history...", Toast.LENGTH_SHORT).show()
        connectToGooglePlay(
            onConnected = {
                val client = billingClient ?: return@connectToGooglePlay
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                client.queryPurchasesAsync(params) { billingResult, purchases ->
                    var hasPro = false
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (purchase in purchases) {
                            if (purchase.products.contains(BillingManager.PRODUCT_ID_PRO) &&
                                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                            ) {
                                hasPro = true
                                handleAcknowledgeIfNeeded(purchase)
                            }
                        }
                    }
                    updateProStatus(hasPro)
                    activity?.let { act ->
                        mainHandler.post {
                            if (hasPro) {
                                Toast.makeText(act, "Pro license restored successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(act, "No previous Pro purchase found for this Google account.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onError = { err ->
                activity?.let { act ->
                    mainHandler.post {
                        Toast.makeText(act, "Google Play error: $err", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
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
