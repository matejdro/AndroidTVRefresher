package com.matejdro.androidtvrefresher

import android.content.Context
import android.preference.PreferenceManager
import androidx.core.content.edit

class Config(context: Context) {
    private val prefs = context.getSharedPreferences("creds", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit {
                putString(KEY_TOKEN, value)
            }
        }

    var url: String?
        get() = prefs.getString(KEY_URL, null)
        set(value) {
            prefs.edit {
                putString(KEY_URL, value)
            }
        }

}

private val KEY_URL = "url"
private val KEY_TOKEN = "token"