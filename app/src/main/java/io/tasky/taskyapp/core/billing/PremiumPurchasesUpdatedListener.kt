package io.tasky.taskyapp.core.billing

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener

class PremiumPurchasesUpdatedListener : PurchasesUpdatedListener {
    var premiumManager: ((BillingResult, List<Purchase>?) -> Unit)? = null

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        premiumManager?.invoke(billingResult, purchases)
    }
} 