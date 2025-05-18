package io.tasky.taskyapp.core.util

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.MutableState
import io.tasky.taskyapp.R
import java.util.Calendar
import java.util.Locale

fun Context.createTimePickerDialog(time: MutableState<String>, isDarkTheme: Boolean): TimePickerDialog {
    val theme = if (isDarkTheme) {
        R.style.Theme_DateDialogDark
    } else {
        R.style.Theme_DateDialogLight
    }

    return with(Calendar.getInstance(Locale.ENGLISH)) {
        TimePickerDialog(
            this@createTimePickerDialog,
            theme,
            { _, hour, minute ->
                time.value = timeToString(hour, minute)
            },
            get(Calendar.HOUR_OF_DAY),
            get(Calendar.MINUTE),
            true
        )
    }
}

fun Context.createDatePickerDialog(
    date: MutableState<String>,
    isDarkTheme: Boolean
): DatePickerDialog {
    val theme = if (isDarkTheme) {
        R.style.Theme_DateDialogDark
    } else {
        R.style.Theme_DateDialogLight
    }

    return with(Calendar.getInstance(Locale.ENGLISH)) {
        DatePickerDialog(
            this@createDatePickerDialog,
            theme,
            { _, year, month, day ->
                date.value = dateToString(day, month, year)
            },
            get(Calendar.YEAR),
            get(Calendar.MONTH),
            get(Calendar.DAY_OF_MONTH)
        )
    }
}

/**
 * Extension function to show a toast message with the specified text.
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Extension function to show a toast message from a string resource.
 */
fun Context.showToast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}