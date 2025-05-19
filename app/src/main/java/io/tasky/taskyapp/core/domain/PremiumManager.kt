package io.tasky.taskyapp.core.domain

import android.app.Activity
import android.content.Context
import android.util.Log
import android.app.AlertDialog
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.core.billing.PremiumPurchasesUpdatedListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PremiumManager"
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    companion object {
        const val MAX_FREE_TASKS = 10
        const val PREMIUM_PRICE = "$4.99"
        const val PREMIUM_PERIOD = "month"
    }

    fun launchPremiumPurchase(activity: Activity) {
        Log.d(TAG, "Starting premium purchase flow")
        
        try {
            // Create and show the subscription details dialog
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Upgrade to Premium")
                .setMessage("""
                    Premium Subscription Details:
                    
                    • Price: $PREMIUM_PRICE per $PREMIUM_PERIOD
                    
                    Would you like to proceed with the purchase?
                """.trimIndent())
                .setPositiveButton("Proceed to Payment") { _, _ ->
                    // Here you would typically redirect to your payment page
                    // For now, we'll just show a toast
                    android.widget.Toast.makeText(
                        activity,
                        "Payment integration coming soon!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            
            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Exception in launchPremiumPurchase", e)
            android.widget.Toast.makeText(activity, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun canAddMoreTasks(currentTaskCount: Int): Boolean {
        return isPremium.value || currentTaskCount < MAX_FREE_TASKS
    }

    // For testing purposes - you can call this to simulate a successful purchase
    fun simulatePremiumPurchase() {
        _isPremium.value = true
    }
} 