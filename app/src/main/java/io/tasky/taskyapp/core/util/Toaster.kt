package io.tasky.taskyapp.core.util

interface Toaster {
    fun showToast(message: String)
    fun showToast(resId: Int)
}
