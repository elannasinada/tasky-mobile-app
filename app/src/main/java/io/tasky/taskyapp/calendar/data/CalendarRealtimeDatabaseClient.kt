package io.tasky.taskyapp.calendar.data

import com.google.firebase.database.DatabaseReference
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

interface CalendarRealtimeDatabaseClient {
    /**
     * Inserts or updates a calendar event reference.
     */
    suspend fun insertCalendarEventRef(user: UserData, eventRef: CalendarEventRef)

    /**
     * Marks a calendar event as deleted in Firebase.
     */
    suspend fun markCalendarEventAsDeleted(user: UserData, eventRef: CalendarEventRef)

    /**
     * Gets all calendar event references for a specific user.
     */
    fun getCalendarEventRefsFromUser(user: UserData): kotlinx.coroutines.flow.Flow<Resource<List<CalendarEventRef>>>
}

class CalendarRealtimeDatabaseClientImpl(
    private val database: DatabaseReference
) : CalendarRealtimeDatabaseClient {
    
    override suspend fun insertCalendarEventRef(user: UserData, eventRef: CalendarEventRef) {
        database.child(user.userId ?: return)
            .child(eventRef.uuid)
            .setValue(eventRef)
            .await()
    }

    override suspend fun markCalendarEventAsDeleted(user: UserData, eventRef: CalendarEventRef) {
        val updatedRef = eventRef.copy(isDeleted = true, needsUpdate = true)
        database.child(user.userId ?: return)
            .child(eventRef.uuid)
            .setValue(updatedRef)
            .await()
    }

    @Suppress("UNCHECKED_CAST")
    override fun getCalendarEventRefsFromUser(user: UserData) = flow {
        emit(Resource.Loading(true))
        
        val result = database.child(user.userId ?: return@flow).get().await()
        val eventRefs = result.children.mapNotNull {
            try {
                CalendarEventRef.fromSnapshot(it.value as HashMap<String, Any?>)
            } catch (e: Exception) {
                null
            }
        }

        emit(
            Resource.Success(
                data = eventRefs.sortedByDescending { it.lastSyncTimestamp }
            )
        )

        emit(Resource.Loading(false))
    }
}