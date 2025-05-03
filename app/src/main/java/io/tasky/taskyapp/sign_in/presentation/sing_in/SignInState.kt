package io.tasky.taskyapp.sign_in.presentation.sing_in

data class SignInState(
    val isSignInSuccessful: Boolean = false,
    val signInError: String? = null
)
