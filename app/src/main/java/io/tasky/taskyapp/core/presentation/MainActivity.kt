package io.tasky.taskyapp.core.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import io.tasky.taskyapp.R
import io.tasky.taskyapp.core.presentation.navigation.MainScreens
import io.tasky.taskyapp.core.util.TASK
import io.tasky.taskyapp.core.util.Toaster
import io.tasky.taskyapp.sign_in.presentation.SignInScreen
import io.tasky.taskyapp.sign_in.presentation.sing_in.SignInEvent
import io.tasky.taskyapp.sign_in.presentation.sing_in.SignInViewModel
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.presentation.details.TaskDetailsEvent
import io.tasky.taskyapp.task.presentation.details.TaskDetailsScreen
import io.tasky.taskyapp.task.presentation.details.TaskDetailsViewModel
import io.tasky.taskyapp.task.presentation.listing.TaskEvent
import io.tasky.taskyapp.task.presentation.listing.TaskViewModel
import io.tasky.taskyapp.task.presentation.listing.TasksScreen
import io.tasky.taskyapp.ui.theme.TaskyTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Activity on which every screen of the app is displayed.
 */
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var toaster: Toaster

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TaskyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    val signInViewModel = hiltViewModel<SignInViewModel>()
                    val signInState = signInViewModel.state.collectAsState().value

                    val taskViewModel = hiltViewModel<TaskViewModel>()
                    val taskState = taskViewModel.state.collectAsState().value

                    val taskDetailsViewModel = hiltViewModel<TaskDetailsViewModel>()
                    val taskDetailsState = taskDetailsViewModel.state.collectAsState().value

                    var startDestination = MainScreens.SignInScreen.name

                    signInViewModel.currentUser?.let {
                        startDestination = MainScreens.TaskScreen.name
                        taskViewModel.userData = it
                        taskDetailsViewModel.userData = it
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(MainScreens.SignInScreen.name) {
                            val launcher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartIntentSenderForResult(),
                                onResult = { result ->
                                    if (result.resultCode == RESULT_OK) {
                                        signInViewModel.onEvent(
                                            SignInEvent.RequestSignInWithGoogle(result.data)
                                        )
                                    }
                                }
                            )

                            LaunchedEffect(key1 = signInState.isSignInSuccessful) {
                                if (signInState.isSignInSuccessful) {
                                    toaster.showToast(R.string.sign_in_successful)

                                    navController.navigate(MainScreens.TaskScreen.name)
                                    signInViewModel.resetState()
                                }
                            }

                            LaunchedEffect(key1 = "launchedEffectKey") {
                                signInViewModel.eventFlow.collectLatest { event ->
                                    when (event) {
                                        is SignInViewModel.UiEvent.ShowToast -> {
                                            toaster.showToast(event.message)
                                        }
                                    }
                                }
                            }

                            SignInScreen(
                                onSignInWithGoogleClick = {
                                    lifecycleScope.launch {
                                        signInViewModel.signIn()?.let {
                                            launcher.launch(
                                                IntentSenderRequest.Builder(
                                                    it
                                                ).build()
                                            )
                                        } ?: toaster.showToast(
                                            R.string.something_went_wrong
                                        )
                                    }
                                },
                                onSignInClick = { email, password, repeatedPassword ->
                                    signInViewModel.onEvent(
                                        SignInEvent.RequestSignIn(
                                            email = email,
                                            password = password,
                                            repeatedPassword = repeatedPassword
                                        )
                                    )
                                },
                                onLoginClick = { email, password ->
                                    signInViewModel.onEvent(
                                        SignInEvent.RequestLogin(
                                            email = email,
                                            password = password,
                                        )
                                    )
                                }
                            )
                        }

                        composable(MainScreens.TaskScreen.name) {
                            signInViewModel.onEvent(SignInEvent.LoadSignedInUser)
                            val userData = signInViewModel.currentUser

                            LaunchedEffect(key1 = "") {
                                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                    taskViewModel.getTasks(userData ?: return@repeatOnLifecycle)
                                }
                            }

                            TasksScreen(
                                navController = navController,
                                userData = userData,
                                state = taskState,
                                onSignOut = {
                                    lifecycleScope.launch {
                                        signInViewModel.onEvent(SignInEvent.RequestLogout)
                                        taskViewModel.clearState()
                                        toaster.showToast(R.string.signed_out)
                                        navController.navigate(MainScreens.SignInScreen.name) {
                                            popUpTo(MainScreens.TaskScreen.name) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                },
                                onRequestDelete = { task ->
                                    taskViewModel.onEvent(TaskEvent.RequestDelete(task))
                                },
                                onRestoreTask = {
                                    taskViewModel.onEvent(TaskEvent.RestoreTask)
                                },
                                onSearchTask = { filter ->
                                    taskViewModel.onEvent(TaskEvent.SearchedForTask(filter))
                                }
                            )
                        }

                        composable(
                            route = MainScreens.TaskDetailsScreen.name + "/{$TASK}",
                            arguments = listOf(
                                navArgument(name = TASK) {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            LaunchedEffect(key1 = MainScreens.TaskDetailsScreen.name) {
                                taskDetailsViewModel.eventFlow.collectLatest { event ->
                                    when (event) {
                                        is TaskDetailsViewModel.UiEvent.Finish -> {
                                            navController.navigateUp()
                                        }
                                        is TaskDetailsViewModel.UiEvent.ShowToast -> {
                                            toaster.showToast(event.message)
                                        }
                                    }
                                }
                            }

                            backStackEntry.arguments?.getString(TASK)?.let {
                                val task = Task.fromJson(URLDecoder.decode(it, StandardCharsets.UTF_8.toString()))
                                taskDetailsState.task = task
                                taskDetailsViewModel.onEvent(TaskDetailsEvent.SetTaskData(task))

                                TaskDetailsScreen(
                                    navController = navController,
                                    state = taskDetailsState,
                                    onRequestInsert = { title, description, date, time, status, isRecurring, recurrencePattern, recurrenceInterval, recurrenceEndDate ->
                                        taskDetailsViewModel.onEvent(
                                            TaskDetailsEvent.RequestInsert(
                                                title = title,
                                                description = description,
                                                date = date,
                                                time = time,
                                                status = status,
                                                isRecurring = isRecurring,
                                                recurrencePattern = recurrencePattern,
                                                recurrenceInterval = recurrenceInterval,
                                                recurrenceEndDate = recurrenceEndDate
                                            )
                                        )
                                    },
                                    onRequestUpdate = { title, description, date, time, status, isRecurring, recurrencePattern, recurrenceInterval, recurrenceEndDate ->
                                        taskDetailsViewModel.onEvent(
                                            TaskDetailsEvent.RequestUpdate(
                                                title = title,
                                                description = description,
                                                date = date,
                                                time = time,
                                                status = status,
                                                isRecurring = isRecurring,
                                                recurrencePattern = recurrencePattern,
                                                recurrenceInterval = recurrenceInterval,
                                                recurrenceEndDate = recurrenceEndDate
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
