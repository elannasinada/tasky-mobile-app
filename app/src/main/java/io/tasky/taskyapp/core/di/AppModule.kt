package io.tasky.taskyapp.core.di

import android.app.Application
import android.content.Context
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.tasky.taskyapp.calendar.data.CalendarRealtimeDatabaseClient
import io.tasky.taskyapp.calendar.data.CalendarRealtimeDatabaseClientImpl
import io.tasky.taskyapp.core.service.NotificationScheduler
import io.tasky.taskyapp.core.util.TaskyToaster
import io.tasky.taskyapp.core.util.TASK
import io.tasky.taskyapp.core.util.Toaster
import io.tasky.taskyapp.sign_in.data.auth_client.GoogleAuthClient
import io.tasky.taskyapp.sign_in.data.auth_client.GoogleAuthClientImpl
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClient
import io.tasky.taskyapp.task.data.remote.RealtimeDatabaseClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providesAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun providesGoogleAuthUiClient(
        app: Application,
        firebaseAuth: FirebaseAuth
    ): GoogleAuthClient {
        return GoogleAuthClientImpl(
            auth = firebaseAuth,
            context = app.applicationContext,
            oneTapClient = Identity.getSignInClient(app.applicationContext)
        )
    }

    @Provides
    @Singleton
    fun providesRealtimeDatabase(): FirebaseDatabase {
        return Firebase.database("https://tasky-2f1b0-default-rtdb.europe-west1.firebasedatabase.app").apply {
        setPersistenceEnabled(true)
        }
    }

    @Provides
    @Singleton
    fun providesRealtimeDatabaseClient(
        database: FirebaseDatabase
    ): RealtimeDatabaseClient {
        return RealtimeDatabaseClientImpl(database.getReference(TASK))
    }

    @Provides
    @Singleton
    fun providesCalendarRealtimeDatabaseClient(
        database: FirebaseDatabase
    ): CalendarRealtimeDatabaseClient {
        return CalendarRealtimeDatabaseClientImpl(database.getReference("calendar_events"))
    }

    @Provides
    @Singleton
    fun providesApplicationContext(app: Application): Context {
        return app.applicationContext
    }

    @Provides
    @Singleton
    fun providesToaster(@ApplicationContext app: Context): Toaster {
        return TaskyToaster(app)
    }

    @Provides
    @Singleton
    fun providesNotificationScheduler(@ApplicationContext context: Context): NotificationScheduler {
        return NotificationScheduler(context)
    }
}
