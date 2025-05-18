package io.tasky.taskyapp.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.tasky.taskyapp.sign_in.data.auth_client.GoogleAuthClient
import io.tasky.taskyapp.sign_in.data.repository.SignInRepositoryImpl
import io.tasky.taskyapp.sign_in.domain.repository.SignInRepository
import io.tasky.taskyapp.sign_in.domain.use_cases.GetSignedInUserUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.LoginWithEmailAndPasswordUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.LogoutUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInUseCases
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInWithEmailAndPasswordUseCase
import io.tasky.taskyapp.sign_in.domain.use_cases.SignInWithGoogleUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SignInSingletonModule {
    
    @Provides
    @Singleton
    fun providesSignInRepository(googleAuthUiClient: GoogleAuthClient): SignInRepository {
        return SignInRepositoryImpl(googleAuthUiClient)
    }

    @Provides
    @Singleton
    fun providesSignInUseCases(repository: SignInRepository): SignInUseCases {
        return SignInUseCases(
            loginWithEmailAndPasswordUseCase = LoginWithEmailAndPasswordUseCase(repository),
            signInWithEmailAndPasswordUseCase = SignInWithEmailAndPasswordUseCase(repository),
            signInWithGoogleUseCase = SignInWithGoogleUseCase(repository),
            signInUseCase = SignInUseCase(repository),
            getSignedInUserUseCase = GetSignedInUserUseCase(repository),
            logoutUseCase = LogoutUseCase(repository)
        )
    }
}