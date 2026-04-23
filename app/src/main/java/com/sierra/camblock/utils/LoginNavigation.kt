package com.sierra.camblock.utils

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import com.sierra.admin.activity.LoginActivity
import com.sierra.camblock.R

object LoginNavigation {

    fun showLoginConfirmation(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.admin_login_dialog_title)
            .setMessage(R.string.admin_login_dialog_message)
            .setPositiveButton(R.string.admin_login_action) { _, _ ->
                navigateToLogin(activity)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun navigateToLogin(activity: Activity) {
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}