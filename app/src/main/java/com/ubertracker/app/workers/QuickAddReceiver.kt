package com.ubertracker.app.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Log receipt of the broadcast
        Log.d("QuickAddReceiver", "Received quick add action")

        // Logic to open the Add Ride dialog directly would go here
        // For now, we can just open the main activity
        val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        mainIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(mainIntent)
    }
}