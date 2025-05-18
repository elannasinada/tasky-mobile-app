package io.tasky.taskyapp.core.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

interface Toaster {
    fun showToast(message: String)
    fun showToast(resId: Int)
    
    companion object {
        /**
         * Show a toast message with the specified text.
         *
         * @param context The context to use
         * @param message The message to display
         * @param duration The duration to show the toast (defaults to Toast.LENGTH_SHORT)
         */
        fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
            Toast.makeText(context, message, duration).show()
        }

        /**
         * Show a toast message from a string resource.
         *
         * @param context The context to use
         * @param stringResId The resource ID of the string to display
         * @param duration The duration to show the toast (defaults to Toast.LENGTH_SHORT)
         */
        fun show(context: Context, @StringRes stringResId: Int, duration: Int = Toast.LENGTH_SHORT) {
            Toast.makeText(context, stringResId, duration).show()
        }
    }
}