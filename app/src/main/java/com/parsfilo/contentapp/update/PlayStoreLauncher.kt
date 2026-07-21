package com.parsfilo.contentapp.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import timber.log.Timber

fun openPlayStore(context: Context) {
    val packageName = context.packageName
    val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
    if (context !is Activity) {
        marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(marketIntent)
    } catch (error: ActivityNotFoundException) {
        Timber.w(error, "Play Store app not found; falling back to web.")
        val webIntent =
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri(),
            )
        if (context !is Activity) {
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}
