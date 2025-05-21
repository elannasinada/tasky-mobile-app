package io.tasky.taskyapp.core.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.AndroidEntryPoint
import io.tasky.taskyapp.R
import io.tasky.taskyapp.calendar.presentation.CalendarScreen
import io.tasky.taskyapp.calendar.presentation.CalendarViewModel
import io.tasky.taskyapp.core.presentation.navigation.MainScreens
import io.tasky.taskyapp.core.service.TaskyNotificationService
import io.tasky.taskyapp.core.util.TASK
import io.tasky.taskyapp.core.util.Toaster
import io.tasky.taskyapp.sign_in.presentation.SignInScreen
import io.tasky.taskyapp.sign_in.presentation.sing_in.SignInEvent
import io.tasky.taskyapp.sign_in.presentation.sing_in.SignInViewModel
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.presentation.details.TaskDetailsEvent
import io.tasky.taskyapp.task.presentation.details.TaskDetailsScreen
import io.tasky.taskyapp.task.presentation.details.TaskDetailsViewModel
import io.tasky.taskyapp.task.presentation.listing.TaskEvent
import io.tasky.taskyapp.task.presentation.listing.TaskViewModel
import io.tasky.taskyapp.task.presentation.listing.TasksScreen
import io.tasky.taskyapp.ui.theme.TaskyTheme
import io.tasky.taskyapp.calendar.presentation.CalendarScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    
    private val TAG = "MainActivity"
    
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted, setting up notifications")
            setupNotifications()
        } else {
            Log.d(TAG, "Notification permission denied")
            toaster.showToast("Notifications disabled. You won't receive task reminders.")
        }
    }
    
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                    setupNotifications()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.d(TAG, "Should show permission rationale")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.d(TAG, "Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            Log.d(TAG, "Android version < 13, notification permission not needed")
            setupNotifications()
        }
    }
    
    private fun setupNotifications() {
        Log.d(TAG, "Setting up notifications")
        TaskyNotificationService.createNotificationChannels(this)
    }

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestNotificationPermission()

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

                            LaunchedEffect(key1 = signInState?.isSignInSuccessful) {
                                if (signInState?.isSignInSuccessful == true) {
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
                                        try {
                                            // First clear any Google sign-in state
                                            val oldGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                            val oldClient = GoogleSignIn.getClient(this@MainActivity, oldGso)
                                            
                                            // Sign out and revoke access
                                            oldClient.signOut().addOnCompleteListener {
                                                oldClient.revokeAccess().addOnCompleteListener {
                                                    // Now proceed with regular logout
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
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // If Google sign-out fails, still perform regular logout
                                            signInViewModel.onEvent(SignInEvent.RequestLogout)
                                            taskViewModel.clearState()
                                            toaster.showToast(R.string.signed_out)
                                            navController.navigate(MainScreens.SignInScreen.name) {
                                                popUpTo(MainScreens.TaskScreen.name) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    }
                                },
                                onRequestDelete = { task ->
                                    taskViewModel.onEvent(TaskEvent.DeleteTask(task))
                                },
                                onRestoreTask = {
                                    taskViewModel.getRecentlyDeletedTask()?.let { task ->
                                        taskViewModel.onEvent(TaskEvent.RestoreTask(task))
                                    }
                                },
                                onSearchTask = { filter ->
                                    taskViewModel.onSearchTask(filter)
                                },
                                onTestNotification = {
                                    // Direct test of notifications - this should immediately show a notification
                                    Log.d(TAG, "Test notification button clicked")
                                    
                                    // Also try to trigger task notifications check
                                    taskViewModel.onEvent(TaskEvent.EnsureNotifications)
                                    
                                    // Create a test task with a deadline in 1 minute
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.MINUTE, 1)
                                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    
                                    val testTask = Task(
                                        uuid = "test-${System.currentTimeMillis()}",
                                        title = "Test Notification Task",
                                        description = "This task tests the notification system",
                                        taskType = "TEST",
                                        deadlineDate = dateFormat.format(cal.time),
                                        deadlineTime = timeFormat.format(cal.time),
                                        status = TaskStatus.PENDING.name
                                    )
                                    
                                    // Schedule a notification for this test task
                                    taskViewModel.onTaskCreated(testTask)
                                    
                                    toaster.showToast("Test notification sent - check your notifications")
                                },
                                viewModel = taskViewModel
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
                                        is TaskDetailsViewModel.UiEvent.ShowPremiumDialog -> {
                                            // Premium dialog is handled internally in the TaskDetailsScreen
                                        }
                                    }
                                }
                            }

                            backStackEntry.arguments?.getString(TASK)?.let {
                                val task = Task.fromJson(URLDecoder.decode(it, StandardCharsets.UTF_8.toString()))
                                taskDetailsState?.task = task
                                taskDetailsViewModel.onEvent(TaskDetailsEvent.SetTaskData(task))

                                TaskDetailsScreen(
                                    navController = navController,
                                    state = taskDetailsState,
                                    onRequestInsert = { title, description, date, time, status, priority, isRecurring, recurrencePattern, recurrenceInterval, recurrenceEndDate ->
                                        taskDetailsViewModel.onEvent(
                                            TaskDetailsEvent.RequestInsert(
                                                title = title,
                                                description = description,
                                                date = date,
                                                time = time,
                                                status = status,
                                                priority = priority,
                                                isRecurring = isRecurring,
                                                recurrencePattern = recurrencePattern,
                                                recurrenceInterval = recurrenceInterval,
                                                recurrenceEndDate = recurrenceEndDate
                                            )
                                        )
                                    },
                                    onRequestUpdate = { title, description, date, time, status, priority, isRecurring, recurrencePattern, recurrenceInterval, recurrenceEndDate ->
                                        taskDetailsViewModel.onEvent(
                                            TaskDetailsEvent.RequestUpdate(
                                                title = title,
                                                description = description,
                                                date = date,
                                                time = time,
                                                status = status,
                                                priority = priority,
                                                isRecurring = isRecurring,
                                                recurrencePattern = recurrencePattern,
                                                recurrenceInterval = recurrenceInterval,
                                                recurrenceEndDate = recurrenceEndDate
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        composable(MainScreens.CalendarScreen.name) {
                            signInViewModel.onEvent(SignInEvent.LoadSignedInUser)
                            val userData = signInViewModel.currentUser
                            
                            val calendarViewModel = hiltViewModel<CalendarViewModel>()
                            
                            val googleSignInLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartIntentSenderForResult(),
                                onResult = { result ->
                                    if (result.resultCode == RESULT_OK) {
                                        signInViewModel.onEvent(
                                            SignInEvent.RequestSignInWithGoogle(result.data)
                                        )
                                        // Return to calendar after sign-in
                                        navController.navigate(MainScreens.CalendarScreen.name)
                                    }
                                }
                            )
                            
                            // For handling calendar permission requests
                            val calendarPermissionLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartActivityForResult()
                            ) { result: androidx.activity.result.ActivityResult ->
                                if (result.resultCode == RESULT_OK) {
                                    toaster.showToast("Sign-in successful, processing...")
                                    
                                    lifecycleScope.launch {
                                        try {
                                            kotlinx.coroutines.delay(500) // Wait half a second
                                            
                                            // Force immediate cache update by requesting account
                                            GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                                            
                                            calendarViewModel.handleSignInResult(result.data)
                                            
                                            // Show CalendarScreen after login
                                            calendarViewModel.loadCalendarEvents(taskState?.tasks ?: emptyList())
                                        } catch (e: Exception) {
                                            toaster.showToast("Error processing sign-in: ${e.message}")
                                        }
                                    }
                                } else {
                                    // Sign-in was canceled or failed
                                    val errorCode = result.resultCode
                                    toaster.showToast("Google sign-in failed with code: $errorCode")
                                    // Try to refresh UI state anyway
                                    calendarViewModel.resetState()
                                }
                            }

                            CalendarScreen(
                                navController = navController,
                                userData = userData,
                                viewModel = calendarViewModel,
                                onGoogleSignInClick = {
                                    // Direct approach: use the calendarViewModel's RequestGoogleSignIn event
                                    // This will use the calendar-specific sign-in flow
                                    calendarViewModel.resetState()
                                    toaster.showToast("Starting Google Calendar sign-in...")
                                },
                                onRequestGoogleSignIn = { options ->
                                    try {
                                        // First clear any existing clients
                                        val oldGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                        val oldClient = GoogleSignIn.getClient(this@MainActivity, oldGso)

                                        // Sign out and then proceed with sign in
                                        oldClient.signOut().addOnCompleteListener {
                                            oldClient.revokeAccess().addOnCompleteListener {
                                                try {
                                                    // Then create new client with the requested options
                                                    val googleSignInClient = GoogleSignIn.getClient(this@MainActivity, options)
                                                    
                                                    // Get web client ID from resources - this is critical for OAuth to work
                                                    val webClientId = getString(R.string.default_web_client_id)
                                                    toaster.showToast("Opening Google sign-in with scopes...")
                                                    
                                                    // Create sign in intent with full scopes
                                                    val signInIntent = googleSignInClient.signInIntent.apply {
                                                        // Add calendar scope explicitly
                                                        putExtra("scope", "email profile https://www.googleapis.com/auth/calendar.readonly")
                                                    }
                                                    
                                                    calendarPermissionLauncher.launch(signInIntent)
                                                } catch (e: Exception) {
                                                    toaster.showToast("Error during sign-in: ${e.message}")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        toaster.showToast("Error launching sign-in: ${e.message}")
                                        android.util.Log.e("MainActivity", "Error launching Google sign-in", e)
                                    }
                                },
                                tasks = taskState?.tasks ?: emptyList()
                            )
                        }
                    }
                }
            }
        }
    }
}