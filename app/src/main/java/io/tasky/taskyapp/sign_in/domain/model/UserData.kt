package io.tasky.taskyapp.sign_in.domain.model

/**
 * Data obtained after a successful authentication attempt.
 */
data class UserData(
    val userId: String?,
    val userName: String?,
    val email: String?,
    val profilePictureUrl: String?,
)
