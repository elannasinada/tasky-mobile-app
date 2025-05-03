package io.tasky.taskyapp.core.di

import android.app.Application
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.tasky.taskyapp.core.util.FakeToaster
import io.tasky.taskyapp.core.util.TASK_TEST
import io.tasky.taskyapp.core.util.Toaster
import io.tasky.taskyapp.sign_in.data.auth_client.GoogleAuthClient
import io.tasky.taskyapp.sign_in.data.auth_client.GoogleAuthClientImpl
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClientImpl
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {
    @Provides
    @Singleton
    fun providesAuth(): FirebaseAuth {
        return Firebase.auth.apply {
            useEmulator("10.0.2.2", 9099)
        }
    }

    @Provides
    @Singleton
    fun providesGoogleAuthUiClient(
        app: Application,
        firebaseAuth: FirebaseAuth
    ): GoogleAuthClient {
        val oneTapClient = Identity.getSignInClient(app.applicationContext)

        return GoogleAuthClientImpl(
            auth = firebaseAuth,
            context = app.applicationContext,
            oneTapClient = oneTapClient
        )
    }

    @Provides
    @Singleton
    fun providesRealtimeDatabase(): FirebaseDatabase {
        return Firebase.database("https://tasky-2f1b0-default-rtdb.europe-west1.firebasedatabase.app").apply {
            setPersistenceEnabled(false)
            useEmulator("10.0.2.2", 9000)
        }
    }

    @Provides
    @Singleton
    fun providesRealtimeDatabaseClient(
        database: FirebaseDatabase
    ): RealtimeDatabaseClient {
        return RealtimeDatabaseClientImpl(database.getReference(TASK_TEST))
    }

    @Provides
    @Singleton
    fun providesToaster(): Toaster {
        return FakeToaster()
    }
}
