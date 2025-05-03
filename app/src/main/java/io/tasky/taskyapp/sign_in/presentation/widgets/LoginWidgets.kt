package io.tasky.taskyapp.sign_in.presentation.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tasky.taskyapp.R
import io.tasky.taskyapp.core.presentation.widgets.TaskyLogoIcon

@Composable
fun HomeLoginBackground() {
    val sizeImage = remember { mutableStateOf(IntSize.Zero) }

    Box {
        Image(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned {
                    sizeImage.value = it.size
                },
            contentScale = ContentScale.Crop,
            painter = painterResource(id = R.drawable.login_background),
            contentDescription = "LoginHomeScreenBackground",
        )
    }
}

@Composable
fun HomeLoginHeader() {
    Column(modifier = Modifier.padding(48.dp)) {
    // just in case I wanted to add some text and change the logic behind the background login page
    }
}
