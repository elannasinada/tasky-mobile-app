package io.tasky.taskyapp.calendar.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.tasky.taskyapp.calendar.data.CalendarRepository
import io.tasky.taskyapp.calendar.data.CalendarRealtimeDatabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    @Provides
    @Singleton
    fun providesCalendarRepository(
        @ApplicationContext context: Context,
        calendarRealtimeDatabaseClient: CalendarRealtimeDatabaseClient,
        auth: FirebaseAuth
    ): CalendarRepository {
        return CalendarRepository(
            context = context,
            calendarRealtimeDatabaseClient = calendarRealtimeDatabaseClient,
            auth = auth
        )
    }
}