package io.tasky.taskyapp.core.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

class TaskyToaster(private val context: Context) : Toaster {
    override fun showToast(message: String) {
        context.showToast(message)
    }

    override fun showToast(resId: Int) {
        context.showToast(resId)
    }
}
