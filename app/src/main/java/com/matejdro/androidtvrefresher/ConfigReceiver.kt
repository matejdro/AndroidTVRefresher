package com.matejdro.androidtvrefresher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ConfigReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getStringExtra("data")?: return

        val config = Config(context)

        when (intent.action) {
            "com.matejdro.androidtvrefresher.SET_URL" -> {
                config.url = data
            }
            "com.matejdro.androidtvrefresher.SET_TOKEN" -> {
                config.token = data
            }
        }
    }
}