package com.parsfilo.contentapp.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.R
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTopBar
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.model.SubscriptionState
import java.util.Locale

@Composable
fun RewardsRoute(
    onBackClick: () -> Unit = {},
    viewModel: RewardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val isAdLoading by viewModel.isAdLoading.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    RewardsScreen(
        uiState = uiState,
        remainingSeconds = remainingSeconds,
        isAdLoading = isAdLoading,
        onBackClick = onBackClick,
        onWatchAd = {
            activity?.let { viewModel.watchRewardedAd(it) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    uiState: RewardsUiState,
    remainingSeconds: Long,
    isAdLoading: Boolean,
    onBackClick: () -> Unit,
    onWatchAd: () -> Unit,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).background(colorScheme.background),
    ) {
        // Top Bar
        AppTopBar(
            title = stringResource(R.string.rewards_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationIconContentDescription = "Geri",
            onNavigationClick = onBackClick,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                ),
        )

        HorizontalDivider(
            thickness = dimens.stroke,
            color = colorScheme.outline.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = dimens.space16),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Premium kullanıcıysa sadece abonelik durumunu göster
            if (uiState.isPremium || uiState.subscriptionState is SubscriptionState.Active) {
                AdFreeInfoCard()
                Spacer(modifier = Modifier.height(dimens.space24))
                return@Column
            }

            // ═══ ÖDÜLLÜ REKLAM BÖLÜMÜ ═══
            RewardAdSection(
                remainingSeconds = remainingSeconds,
                watchCount = uiState.watchCount,
                nextRewardMinutes = uiState.nextRewardMinutes,
                isAdLoading = isAdLoading,
                onWatchAd = onWatchAd,
            )

            Spacer(modifier = Modifier.height(dimens.space16))
        }
    }
}

@Composable
private fun AdFreeInfoCard() {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    AppCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = dimens.space16),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(dimens.radiusLarge),
        colors =
            CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.88f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimens.space24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(dimens.iconXl),
            )
            Spacer(modifier = Modifier.height(dimens.space12))
            Text(
                text = "Reklamlar Devre Dışı",
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
            )
            Spacer(modifier = Modifier.height(dimens.space8))
            Text(
                text = "Bu hesapta reklam gösterimi kapalı olduğu için ödüllü reklam bölümü kullanılmıyor.",
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun RewardAdSection(
    remainingSeconds: Long,
    watchCount: Int,
    nextRewardMinutes: Int,
    isAdLoading: Boolean,
    onWatchAd: () -> Unit,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(dimens.radiusLarge),
        colors =
            CardDefaults.cardColors(
                containerColor = colorScheme.surface,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimens.space20),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Başlık
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(dimens.space28),
                )
                Spacer(modifier = Modifier.width(dimens.space8))
                Text(
                    text = "Ücretsiz Reklamsız Süre",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                )
            }

            Spacer(modifier = Modifier.height(dimens.space16))

            // Kalan süre göstergesi
            if (remainingSeconds > 0) {
                ActiveRewardTimer(remainingSeconds = remainingSeconds)
                Spacer(modifier = Modifier.height(dimens.space16))
            }

            // Bilgilendirme
            Text(
                text = "Reklam izleyerek reklamsız süre kazanın!",
                color = colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(dimens.space8))

            // Ödül tablosu
            RewardTierInfo()

            Spacer(modifier = Modifier.height(dimens.space16))

            // İzleme sayısı
            if (watchCount > 0) {
                Text(
                    text = "Bugün $watchCount reklam izlediniz",
                    color = colorScheme.secondary,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(dimens.space8))
            }

            // Reklam izle butonu
            AppButton(
                onClick = onWatchAd,
                enabled = !isAdLoading,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dimens.buttonHeight),
                shape =
                    androidx.compose.foundation.shape.RoundedCornerShape(
                        dimens.radiusMedium,
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                        disabledContainerColor = colorScheme.primary.copy(alpha = 0.5f),
                    ),
                text = "",
            ) {
                if (isAdLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimens.iconSm),
                        color = colorScheme.onPrimary,
                        strokeWidth = dimens.strokeThin,
                    )
                    Spacer(modifier = Modifier.width(dimens.space8))
                    Text(
                        text = "Yükleniyor...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(dimens.iconMd),
                    )
                    Spacer(modifier = Modifier.width(dimens.space8))
                    Text(
                        text = "Reklam İzle → $nextRewardMinutes dk Kazan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveRewardTimer(remainingSeconds: Long) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeText = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(dimens.radiusMedium),
        colors =
            CardDefaults.cardColors(
                containerColor = colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(dimens.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = colorScheme.primary.copy(alpha = pulseAlpha),
                modifier = Modifier.size(dimens.iconMd),
            )
            Spacer(modifier = Modifier.width(dimens.space12))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Reklamsız Süre Aktif",
                    color = colorScheme.primary.copy(alpha = pulseAlpha),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = timeText,
                    color = colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                )
            }
        }
    }
}

@Composable
private fun RewardTierInfo() {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(dimens.radiusSmall),
        colors =
            CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(dimens.space12),
        ) {
            RewardTierRow("1. reklam", "30 dk reklamsız")
            RewardTierRow("2. reklam", "60 dk reklamsız")
            RewardTierRow("3. reklam", "90 dk reklamsız")
        }
    }
}

@Composable
private fun RewardTierRow(
    label: String,
    reward: String,
) {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.elevationMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text(
            text = reward,
            color = colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
