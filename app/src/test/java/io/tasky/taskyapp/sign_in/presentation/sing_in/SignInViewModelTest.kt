package io.tasky.taskyapp.sign_in.presentation.sing_in

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isTrue
import io.tasky.taskyapp.sign_in.data.repository.FakeSignInRepository
import io.tasky.taskyapp.sign_in.domain.repository.SignInRepository
import io.tasky.taskyapp.sign_in.domain.use_cases.GetSignedInUserUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.LoginWithEmailAndPasswordUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.LogoutUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInUseCases
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInWithEmailAndPasswordUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInWithGoogleUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.userData
import io.tasky.taskyapp.util.MainCoroutineExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
internal class SignInViewModelTest {
    private lateinit var repository: SignInRepository
    private lateinit var useCases: SignInUseCases
    private lateinit var viewModel: SignInViewModel

    @BeforeEach
    fun setUp() {
        repository = FakeSignInRepository()
        useCases = SignInUseCases(
            loginWithEmailAndPasswordUseCase = LoginWithEmailAndPasswordUseCase(repository),
            signInWithEmailAndPasswordUseCase = SignInWithEmailAndPasswordUseCase(repository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(repository),
            signInUseCase = SignInUseCase(repository),
            getSignedInUserUseCase = GetSignedInUserUseCase(repository),
            logoutUseCase = LogoutUseCase(repository)
        )
        viewModel = SignInViewModel(useCases)
    }

    @Test
    fun `Getting signed in user, returns null`() = runTest {
        viewModel.onEvent(SignInEvent.LoadSignedInUser)
        advanceUntilIdle()

        assertThat(viewModel.currentUser).isNull()
    }

    @Test
    fun `Getting signed in user after a login, returns the user`() = runTest {
        viewModel.onEvent(
            SignInEvent.RequestLogin(
                "fellow@user.com",
                "password"
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(SignInEvent.LoadSignedInUser)
        advanceUntilIdle()

        assertThat(viewModel.currentUser).isEqualTo(
            userData()
        )
    }

    @Test
    fun `Logging out right after logging in, returns null`() = runTest {
        viewModel.onEvent(
            SignInEvent.RequestLogin(
                "fellow@user.com",
                "password"
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(SignInEvent.RequestLogout)
        advanceUntilIdle()

        viewModel.onEvent(SignInEvent.LoadSignedInUser)
        advanceUntilIdle()

        assertThat(viewModel.currentUser).isNull()
    }

    @Test
    fun `Getting signed in user after a sign in, returns the user`() = runTest {
        viewModel.onEvent(
            SignInEvent.RequestSignIn(
                "fellow@user.com",
                "password",
                "password"
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(SignInEvent.LoadSignedInUser)
        advanceUntilIdle()

        assertThat(viewModel.currentUser).isEqualTo(
            userData().copy(
                userId = "randomNewUserId"
            )
        )
    }

    @Test
    fun `Signing in, state is success`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellow@user.com",
                    "password",
                    "password"
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isTrue()
            assertThat(emission2.signInError).isNullOrEmpty()
        }
    }

    @Test
    fun `Signing in with no password, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellow@user.com",
                    "",
                    "password"
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write your password")
        }
    }

    @Test
    fun `Signing in with no password, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellow@user.com",
                    "",
                    "password"
                )
            )

            val emission = awaitItem()
            assertThat(emission)
                .isEqualTo(
                    SignInViewModel.UiEvent.ShowToast(
                        "Write your password"
                    )
                )
        }
    }

    @Test
    fun `Signing in with password not matching, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellow@user.com",
                    "password",
                    "wrongPassword"
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("The passwords don't match")
        }
    }

    @Test
    fun `Signing in with password not matching, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellow@user.com",
                    "password",
                    "wrongPassword"
                )
            )

            val emission = awaitItem()
            assertThat(emission)
                .isEqualTo(
                    SignInViewModel.UiEvent.ShowToast(
                        "The passwords don't match"
                    )
                )
        }
    }

    @Test
    fun `Signing in with no email, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "",
                    "password",
                    "password"
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write a valid email")
        }
    }

    @Test
    fun `Signing in with no email, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "",
                    "password",
                    "password"
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                SignInViewModel.UiEvent.ShowToast(
                    "Write a valid email"
                )
            )
        }
    }

    @Test
    fun `Signing in with invalid email, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellowuser.com",
                    "password",
                    "password"
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write a valid email")
        }
    }

    @Test
    fun `Signing in with invalid email, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestSignIn(
                    "fellowuser.com",
                    "password",
                    "password"
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                SignInViewModel.UiEvent.ShowToast(
                    "Write a valid email"
                )
            )
        }
    }

    @Test
    fun `Logging in, state is success`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "fellow@user.com",
                    "password",
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isTrue()
            assertThat(emission2.signInError).isNullOrEmpty()
        }
    }

    @Test
    fun `Logging in with no password, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "fellow@user.com",
                    "",
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write your password")
        }
    }

    @Test
    fun `Logging in with no password, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "fellow@user.com",
                    "",
                )
            )

            val emission = awaitItem()
            assertThat(emission)
                .isEqualTo(
                    SignInViewModel.UiEvent.ShowToast(
                        "Write your password"
                    )
                )
        }
    }

    @Test
    fun `Logging in with no email, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "",
                    "password",
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write a valid email")
        }
    }

    @Test
    fun `Logging in with no email, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "",
                    "password",
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                SignInViewModel.UiEvent.ShowToast(
                    "Write a valid email"
                )
            )
        }
    }

    @Test
    fun `Logging in with invalid email, state has error message`() = runTest {
        viewModel.state.test {
            val emission1 = awaitItem()
            assertThat(emission1.isSignInSuccessful).isFalse()
            assertThat(emission1.signInError).isNull()

            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "fellowuser.com",
                    "password",
                )
            )

            val emission2 = awaitItem()
            assertThat(emission2.isSignInSuccessful).isFalse()
            assertThat(emission2.signInError).isEqualTo("Write a valid email")
        }
    }

    @Test
    fun `Logging in with invalid email, shows toast`() = runTest {
        viewModel.eventFlow.test {
            viewModel.onEvent(
                SignInEvent.RequestLogin(
                    "fellowuser.com",
                    "password",
                )
            )

            val emission = awaitItem()
            assertThat(emission).isEqualTo(
                SignInViewModel.UiEvent.ShowToast(
                    "Write a valid email"
                )
            )
        }
    }

    @Test
    fun `Resetting state, makes the state brand new`() = runTest {
        viewModel.onEvent(
            SignInEvent.RequestLogin(
                "fellow@user.com",
                "password",
            )
        )
        advanceUntilIdle()

        val state = viewModel.state
        assertThat(state.value.isSignInSuccessful).isTrue()
        assertThat(state.value.signInError).isNullOrEmpty()

        viewModel.resetState()
        advanceUntilIdle()

        assertThat(state.value.isSignInSuccessful).isFalse()
        assertThat(state.value.signInError).isNull()
    }
}
