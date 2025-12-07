package com.ubertracker.app.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        mainIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(mainIntent)
    }
}