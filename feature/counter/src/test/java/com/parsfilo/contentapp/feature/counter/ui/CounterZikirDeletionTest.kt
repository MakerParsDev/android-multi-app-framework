package com.parsfilo.contentapp.feature.counter.ui

import com.google.common.truth.Truth.assertThat
import com.parsfilo.contentapp.feature.counter.model.CounterUiState
import com.parsfilo.contentapp.feature.counter.model.ZikirItem
import org.junit.Test

class CounterZikirDeletionTest {
    private val a = item("default_a", 33)
    private val b = item("custom_b", 99)
    private val c = item("default_c", 100)

    @Test fun unrelatedDefaultDeletionPreservesActiveCountAndSession() {
        val state = activeState(listOf(a, b, c))
        val after = state.afterZikirDeletion(c)!!

        assertThat(after.zikirList).containsExactly(a, b).inOrder()
        assertThat(after.selectedZikir).isEqualTo(a)
        assertThat(after.currentCount).isEqualTo(17)
        assertThat(after.isSessionActive).isTrue()
        assertThat(after.sessionStartTime).isEqualTo(12345L)
        assertThat(after.targetCount).isEqualTo(33)
    }

    @Test fun unrelatedCustomDeletionPreservesActiveProgress() {
        val state = activeState(listOf(a, b, c))
        val after = state.afterZikirDeletion(b)!!
        assertThat(after.selectedZikir).isEqualTo(a)
        assertThat(after.currentCount).isEqualTo(17)
        assertThat(after.sessionStartTime).isEqualTo(12345L)
        assertThat(after.isSessionActive).isTrue()
        assertThat(after.zikirList).containsExactly(a, c).inOrder()
    }

    @Test fun deletingSelectedResetsUnfinishedProgressAndPicksReplacement() {
        val after = activeState(listOf(a, b)).afterZikirDeletion(a)!!
        assertThat(after.zikirList).containsExactly(b)
        assertThat(after.selectedZikir).isEqualTo(b)
        assertThat(after.targetCount).isEqualTo(b.defaultTarget)
        assertThat(after.currentCount).isEqualTo(0)
        assertThat(after.isSessionActive).isFalse()
        assertThat(after.sessionStartTime).isEqualTo(0L)
        assertThat(after.showZikirSelector).isTrue()
    }

    @Test fun lastItemDeletionRetainsRecoverableEmptySelector() {
        val after = activeState(listOf(a)).afterZikirDeletion(a)!!
        assertThat(after.zikirList).isEmpty()
        assertThat(after.selectedZikir).isNull()
        assertThat(after.showZikirSelector).isTrue()
        assertThat(after.currentCount).isEqualTo(0)
    }

    @Test fun inactiveSelectedDeletionReplacesSelectionWithoutProgress() {
        val after = activeState(listOf(a, b)).copy(
            currentCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
        ).afterZikirDeletion(a)!!
        assertThat(after.selectedZikir).isEqualTo(b)
        assertThat(after.currentCount).isEqualTo(0)
        assertThat(after.isSessionActive).isFalse()
    }

    @Test fun unknownAndRepeatedDeletionAreIdempotent() {
        val state = activeState(listOf(a, b))
        assertThat(state.afterZikirDeletion(c)).isNull()
        val once = state.afterZikirDeletion(b)!!
        assertThat(once.afterZikirDeletion(b)).isNull()
        assertThat(once.currentCount).isEqualTo(17)
        assertThat(once.selectedZikir).isEqualTo(a)
    }

    private fun activeState(items: List<ZikirItem>) = CounterUiState(
        zikirList = items,
        selectedZikir = a,
        currentCount = 17,
        targetCount = a.defaultTarget,
        isSessionActive = true,
        sessionStartTime = 12345L,
        showZikirSelector = true,
        todayTotalCount = 99,
    )

    private fun item(key: String, target: Int) = ZikirItem(
        key = key,
        arabicText = key,
        latinText = key,
        turkishMeaning = key,
        defaultTarget = target,
        virtue = "",
        virtueSource = "",
    )
}
