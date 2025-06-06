package io.tasky.taskyapp.sign_in.domain.use_cases

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.tasky.taskyapp.sign_in.data.repository.FakeSignInRepository
import io.tasky.taskyapp.sign_in.domain.repository.SignInRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SignInWithGoogleUseCaseTest {
    private lateinit var repository: SignInRepository
    private lateinit var useCase: SignInWithGoogleUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeSignInRepository()
        useCase = SignInWithGoogleUseCase(repository)
    }

    @Test
    fun `Signing in with Google, returns the Google user`() = runTest {
        val result = useCase.invoke(mockk(relaxed = true))
        assertThat(result.data).isEqualTo(
            userData().copy(
                userId = "randomGoogleUserId",
                email = "googlefellow@user.com",
            )
        )
    }
}
