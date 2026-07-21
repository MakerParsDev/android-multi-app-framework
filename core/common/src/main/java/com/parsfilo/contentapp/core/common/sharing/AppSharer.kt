package com.parsfilo.contentapp.core.common.sharing

import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * WhatsApp app recommendation sharer.
 * USAGE: Only for recommending the app. NO verse sharing!
 */
object AppSharer {
    private const val WHATSAPP_PACKAGE = "com.whatsapp"

    /**
     * Uygulamayı WhatsApp üzerinden arkadaşlara tavsiye et.
     * Play Store linki ile paylaşır.
     *
     * @param context Context
     * @param appName Uygulama ismi
     * @param playStoreUrl Play Store URL
     */
    fun shareApp(
        context: Context,
        appName: String,
        playStoreUrl: String,
    ) {
        val message =
            """
            📖 $appName uygulamasını kullanıyorum, çok beğendim!

            Sen de indir: $playStoreUrl
            """.trimIndent()

        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                `package` = WHATSAPP_PACKAGE
                putExtra(Intent.EXTRA_TEXT, message)
            }

        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "WhatsApp not installed, showing general share sheet")
            // WhatsApp yüklü değil - genel paylaşım
            val fallback =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                }
            val chooser = Intent.createChooser(fallback, "Uygulamayı Paylaş")
            context.startActivity(chooser)
        }
    }
}
