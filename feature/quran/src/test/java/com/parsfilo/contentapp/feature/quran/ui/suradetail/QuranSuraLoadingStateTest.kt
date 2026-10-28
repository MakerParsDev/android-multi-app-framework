package com.parsfilo.contentapp.feature.quran.ui.suradetail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuranSuraLoadingStateTest {
    @Test
    fun emptyReaderRemainsLoadingWhileEditionRequestsArePending() {
        assertThat(shouldShowSuraLoading(isSyncing = true, hasAyahs = false)).isTrue()
    }

    @Test
    fun cachedContentRemainsReadableWhileOptionalEditionsRefresh() {
        assertThat(shouldShowSuraLoading(isSyncing = true, hasAyahs = true)).isFalse()
    }

    @Test
    fun failedLoadExposesErrorInsteadOfSpinningIndefinitely() {
        assertThat(shouldShowSuraLoading(isSyncing = false, hasAyahs = false)).isFalse()
    }

    @Test
    fun completedLoadShowsCachedOrFreshContent() {
        assertThat(shouldShowSuraLoading(isSyncing = false, hasAyahs = true)).isFalse()
    }
}
