package io.tasky.taskyapp.sign_in.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
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

@Module
@InstallIn(ViewModelComponent::class)
object SignInModule {
    // All providers removed to avoid duplicate bindings with SignInSingletonModule
}