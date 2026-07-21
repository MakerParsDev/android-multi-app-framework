package com.parsfilo.contentapp.feature.quran.ui.bookmarks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTopBar
import com.parsfilo.contentapp.feature.quran.R

@Composable
fun QuranBookmarksRoute(
    onAyahClick: (suraNumber: Int, ayahNumber: Int) -> Unit,
    onBack: () -> Unit,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
    viewModel: QuranBookmarksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    QuranBookmarksScreen(
        state = uiState,
        onBack = onBack,
        onAyahClick = onAyahClick,
        onRemoveBookmark = viewModel::removeBookmark,
        bannerAdContent = bannerAdContent,
        nativeAdContent = nativeAdContent,
    )
}

@Composable
fun QuranBookmarksScreen(
    state: QuranBookmarksUiState,
    onBack: () -> Unit,
    onAyahClick: (suraNumber: Int, ayahNumber: Int) -> Unit,
    onRemoveBookmark: (suraNumber: Int, ayahNumber: Int) -> Unit,
    bannerAdContent: (@Composable () -> Unit)? = null,
    nativeAdContent: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.quran_bookmarks_title),
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
        bottomBar = {
            if (state.shouldShowAds) {
                bannerAdContent?.invoke()
            }
        },
    ) { innerPadding ->
        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.quran_bookmarks_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(bottom = 90.dp),
        ) {
            itemsIndexed(
                items = state.items,
                key = { _, item -> "${item.bookmark.suraNumber}_${item.bookmark.ayahNumber}" },
            ) { index, item ->
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clickable { onAyahClick(item.bookmark.suraNumber, item.bookmark.ayahNumber) },
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.suraName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.quran_last_read_format,
                                item.bookmark.suraNumber,
                                item.bookmark.ayahNumber,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quran_remove_bookmark),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable {
                                onRemoveBookmark(item.bookmark.suraNumber, item.bookmark.ayahNumber)
                            },
                        )
                    }
                }

                if (state.shouldShowAds && nativeAdContent != null && (index + 1) % 4 == 0 && index < state.items.lastIndex) {
                    nativeAdContent()
                }
            }
        }
    }
}
