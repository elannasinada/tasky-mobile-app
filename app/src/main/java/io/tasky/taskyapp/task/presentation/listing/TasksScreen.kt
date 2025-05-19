package io.tasky.taskyapp.task.presentation.listing

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import io.tasky.taskyapp.R
import io.tasky.taskyapp.core.presentation.PremiumDialog
import io.tasky.taskyapp.core.presentation.navigation.DrawerBody
import io.tasky.taskyapp.core.presentation.navigation.DrawerHeader
import io.tasky.taskyapp.core.presentation.navigation.MainScreens
import io.tasky.taskyapp.core.presentation.navigation.MenuItem
import io.tasky.taskyapp.core.presentation.widgets.TaskAppBar
import io.tasky.taskyapp.core.util.DefaultSearchBar
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.presentation.widgets.NewTaskBottomSheetContent
import io.tasky.taskyapp.task.presentation.widgets.TaskCard
import io.tasky.taskyapp.task.presentation.widgets.TaskShimmerCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@ExperimentalMaterialApi
@Composable
fun TasksScreen(
    navController: NavHostController,
    userData: UserData?,
    state: TaskState,
    onSignOut: () -> Unit,
    onRequestDelete: (Task) -> Unit,
    onRestoreTask: () -> Unit,
    onSearchTask: (String) -> Unit,
    onTestNotification: () -> Unit = {},
    viewModel: TaskViewModel
) {
    val selectedTaskType = remember {
        mutableStateOf("")
    }

    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        confirmValueChange = {
            if (it == ModalBottomSheetValue.Hidden) {
                selectedTaskType.value = ""
            }
            true
        }
    )
    val lifecycle = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val showDebugDialog = remember { mutableStateOf(false) }
    val debugMessage = remember { mutableStateOf("") }
    val activity = context as? Activity
    val isActivityAvailable = activity != null

    BackHandler(enabled = bottomSheetState.isVisible) {
        coroutineScope.launch {
            bottomSheetState.hide()
        }
    }

    LaunchedEffect(key1 = "launchedEffectKey") {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (bottomSheetState.isVisible)
                bottomSheetState.hide()
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp),
        sheetBackgroundColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetContent = {
            NewTaskBottomSheetContent(selectedTaskType) {
                coroutineScope.launch {
                    bottomSheetState.hide()
                }
                val taskJson = Task(title = "", taskType = selectedTaskType.value).toJson()
                val encodedTaskJson = URLEncoder.encode(taskJson, StandardCharsets.UTF_8.toString())
                navController.navigate(
                    MainScreens.TaskDetailsScreen.name + "/$encodedTaskJson"
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TasksScaffold(
                navController = navController,
                coroutineScope = coroutineScope,
                bottomSheetState = bottomSheetState,
                userData = userData,
                state = state,
                onRequestDelete = onRequestDelete,
                onRestoreTask = onRestoreTask,
                onSearchTask = onSearchTask,
                onTestNotification = onTestNotification,
                onSignOut = onSignOut,
                onShowPremiumDialog = { viewModel.onPremiumDialogShow() }
            )

            if (state.showPremiumDialog) {
                PremiumDialog(
                    onDismiss = { viewModel.onPremiumDialogDismiss() },
                    onUpgrade = {
                        if (isActivityAvailable) {
                            try {
                                viewModel.onPremiumUpgrade(activity!!)
                            } catch (e: Exception) {
                                debugMessage.value = "Billing Error: ${e.message ?: "Unknown error"}"
                                showDebugDialog.value = true
                            }
                        } else {
                            Log.e("TasksScreen", "Context is not an Activity: $context")
                        }
                    }
                )
            }

            if (showDebugDialog.value) {
                Dialog(
                    onDismissRequest = { showDebugDialog.value = false },
                    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(0.9f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.material.Text(
                                text = "Debug Info",
                                style = MaterialTheme.typography.titleMedium
                            )
                            androidx.compose.material.Text(
                                text = debugMessage.value,
                                modifier = Modifier.padding(vertical = 16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material.Button(
                                onClick = { showDebugDialog.value = false }
                            ) {
                                androidx.compose.material.Text(text = "Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalMaterialApi
@ExperimentalMaterial3Api
@Composable
private fun TasksScaffold(
    navController: NavHostController,
    coroutineScope: CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    userData: UserData?,
    state: TaskState,
    onRequestDelete: (Task) -> Unit,
    onRestoreTask: () -> Unit,
    onSearchTask: (String) -> Unit,
    onTestNotification: () -> Unit = {},
    onSignOut: () -> Unit,
    onShowPremiumDialog: () -> Unit
) {
    val context = LocalContext.current
    val search = remember {
        mutableStateOf("")
    }

    val snackBarHostState = remember {
        SnackbarHostState()
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(userData = userData)
                DrawerBody(
                    items = listOf(
                        MenuItem(
                            id = "Home",
                            title = stringResource(id = R.string.home),
                            contentDescription = "Home Button",
                            icon = Icons.Default.Home
                        ),
                        MenuItem(
                            id = "Calendar",
                            title = stringResource(id = R.string.calendar),
                            contentDescription = "Calendar Button",
                            icon = Icons.Default.CalendarMonth
                        ),
                        MenuItem(
                            id = "Logout",
                            title = stringResource(id = R.string.logout),
                            contentDescription = "Logout Button",
                            icon = Icons.Default.ExitToApp
                        )
                    ),
                    onItemClick = {
                        when (it.id) {
                            "Home" -> {
                                coroutineScope.launch {
                                    drawerState.close()
                                }
                            }

                            "Calendar" -> {
                                coroutineScope.launch {
                                    drawerState.close()
                                    navController.navigate(MainScreens.CalendarScreen.name)
                                }
                            }

                            "Logout" -> {
                                onSignOut.invoke()
                            }
                        }
                    }
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TaskAppBar {
                    coroutineScope.launch {
                        drawerState.open()
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackBarHostState)
            },
            floatingActionButton = {
                FloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        if (state.tasks.size >= 10) {
                            onShowPremiumDialog()
                        } else {
                            coroutineScope.launch {
                                bottomSheetState.show()
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Create new task",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DefaultSearchBar(
                    search = search,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 0.dp
                    ),
                    onSearch = { filter ->
                        onSearchTask.invoke(filter)
                    },
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.loading) {
                        items(3) {
                            TaskShimmerCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    } else {
                        itemsIndexed(state.tasks) { index, task ->
                            TaskCard(
                                task = task,
                                onClickCard = {
                                    val taskJson = task.toJson()
                                    val encodedTaskJson = URLEncoder.encode(taskJson, StandardCharsets.UTF_8.toString())
                                    navController.navigate(
                                        MainScreens.TaskDetailsScreen.name + "/$encodedTaskJson"
                                    )
                                },
                                onClickDelete = {
                                    onRequestDelete.invoke(task)

                                    coroutineScope.launch {
                                        val result = snackBarHostState.showSnackbar(
                                            message = context.getString(R.string.task_deleted),
                                            actionLabel = context.getString(R.string.undo)
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestoreTask.invoke()
                                        }
                                    }
                                },
                                modifier = Modifier.padding(
                                    top = if (index == 0) 8.dp else 0.dp,
                                    bottom = 0.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}