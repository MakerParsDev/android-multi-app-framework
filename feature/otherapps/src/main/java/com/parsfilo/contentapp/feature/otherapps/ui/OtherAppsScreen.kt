package com.parsfilo.contentapp.feature.otherapps.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTopBar
import com.parsfilo.contentapp.core.designsystem.theme.app_transparent
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.otherapps.R
import com.parsfilo.contentapp.feature.otherapps.model.OtherApp

@Composable
fun OtherAppsRoute(
    viewModel: OtherAppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OtherAppsScreen(
        uiState = uiState,
        onRetry = viewModel::refresh
    )
}

@Composable
fun OtherAppsScreen(
    uiState: OtherAppsUiState,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.other_apps_title),
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = app_transparent
            ),
            titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )

        HorizontalDivider(
            thickness = dimens.stroke,
            color = colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = dimens.space24, vertical = dimens.space4)
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }

            uiState.errorMessage != null && uiState.apps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.space24),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.other_apps_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(dimens.space12))
                        AppButton(
                            text = stringResource(R.string.other_apps_retry),
                            onClick = onRetry
                        )
                    }
                }
            }

            uiState.apps.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimens.space24),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.other_apps_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = dimens.space12, vertical = dimens.space8),
                    verticalArrangement = Arrangement.spacedBy(dimens.space14),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = uiState.apps,
                        key = { index, app -> "${app.packageName}_$index" },
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "market://details?id=${app.packageName}".toUri())
                                    )
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://play.google.com/store/apps/details?id=${app.packageName}".toUri()
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItem(app: OtherApp, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.space14),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = dimens.elevationMedium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.space14, vertical = dimens.space14)
        ) {
            SubcomposeAsyncImage(
                model = app.appIconUrl,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(dimens.topBarHeight)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconMd),
                            strokeWidth = dimens.space2,
                            color = colorScheme.primary
                        )
                    }
                },
                success = { SubcomposeAsyncImageContent() },
                error = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = app.appName.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.primary
                        )
                    }
                }
            )

            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = dimens.space12)
            )

            if (app.isNew) {
                Surface(
                    color = Color(0xFFFF6F00),
                    shape = RoundedCornerShape(dimens.radiusSmall),
                    modifier = Modifier.padding(end = dimens.space6)
                ) {
                    Text(
                        text = stringResource(R.string.other_apps_new),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = dimens.space8, vertical = dimens.space4)
                    )
                }
            }

            AppButton(
                text = stringResource(R.string.other_apps_install),
                onClick = onClick,
                shape = RoundedCornerShape(dimens.radiusXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                contentPadding = PaddingValues(horizontal = dimens.space12, vertical = dimens.space8),
            ) {
                Text(
                    text = stringResource(R.string.other_apps_install),
                    color = colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
