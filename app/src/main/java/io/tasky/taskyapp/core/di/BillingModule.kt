package io.tasky.taskyapp.core.di

import android.content.Context
import com.android.billingclient.api.BillingClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.tasky.taskyapp.core.billing.PremiumPurchasesUpdatedListener
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun providePurchasesUpdatedListener(): PremiumPurchasesUpdatedListener {
        return PremiumPurchasesUpdatedListener()
    }

    @Provides
    @Singleton
    fun provideBillingClient(
        @ApplicationContext context: Context,
        listener: PremiumPurchasesUpdatedListener
    ): BillingClient {
        return BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
    }
} 