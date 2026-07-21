package com.parsfilo.contentapp.feature.prayertimes.ui

import android.content.Context
import androidx.annotation.StringRes

sealed class UiText {
    data class StringResource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText()

    data class DynamicString(val value: String) : UiText()
}

fun UiText.asString(context: Context): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.StringResource -> context.getString(resId, *args.toTypedArray())
    }
}
