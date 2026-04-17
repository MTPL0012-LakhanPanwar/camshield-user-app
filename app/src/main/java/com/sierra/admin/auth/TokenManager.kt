package com.sierra.admin.auth

import android.content.Context
import com.sierra.admin.Constants

class TokenManager(context: Context) {
    private val prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    fun saveToken(token: String) = prefs.edit().putString(Constants.KEY_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(Constants.KEY_TOKEN, null)

    fun saveAdmin(id: String, username: String) {
        prefs.edit()
            .putString(Constants.KEY_ADMIN_ID, id)
            .putString(Constants.KEY_ADMIN_USERNAME, username)
            .apply()
    }

    fun getAdminUsername(): String? = prefs.getString(Constants.KEY_ADMIN_USERNAME, null)
    fun getAdminId(): String? = prefs.getString(Constants.KEY_ADMIN_ID, null)
    fun isLoggedIn(): Boolean = getToken() != null

    fun clearAll() {
        prefs.edit()
            .remove(Constants.KEY_TOKEN)
            .remove(Constants.KEY_ADMIN_ID)
            .remove(Constants.KEY_ADMIN_USERNAME)
            .apply()
    }
}
