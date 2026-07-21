package com.parsfilo.contentapp.feature.billing.ui

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsfilo.contentapp.core.designsystem.AppTheme
import com.parsfilo.contentapp.core.designsystem.component.AppButton
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTopBar
import com.parsfilo.contentapp.core.designsystem.theme.app_transparent
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.core.designsystem.tokens.LocalMotion
import com.parsfilo.contentapp.core.model.SubscriptionState
import com.parsfilo.contentapp.feature.billing.BillingErrorKeys
import com.parsfilo.contentapp.feature.billing.R
import com.parsfilo.contentapp.feature.billing.model.BillingProduct

@Composable
fun SubscriptionRoute(
    onBackClick: () -> Unit = {},
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    SubscriptionScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onPlanSelected = { productPackage ->
            activity?.let { act ->
                viewModel.billingManager.launchBillingFlow(act, productPackage)
            }
        }
    )
}

@Composable
fun SubscriptionScreen(
    uiState: SubscriptionUiState,
    onBackClick: () -> Unit = {},
    onPlanSelected: (BillingProduct) -> Unit
) {
    val dimens = LocalDimens.current
    val motion = LocalMotion.current
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = motion.durationMedium,
                    easing = motion.emphasizedEasing
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = dimens.space16),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppTopBar(
            title = stringResource(R.string.billing_subscription_title),
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationIconContentDescription = stringResource(R.string.billing_back),
            onNavigationClick = onBackClick,
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = com.parsfilo.contentapp.core.designsystem.theme.app_transparent,
            ),
        )

        Spacer(modifier = Modifier.height(dimens.space12))

        // Header
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(dimens.iconXl)
        )
        Spacer(modifier = Modifier.height(dimens.space12))
        Text(
            text = stringResource(R.string.billing_subscription_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(dimens.space4))
        Text(
            text = stringResource(R.string.billing_subscription_description),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(dimens.space24))

        when (uiState.subscriptionState) {
            is SubscriptionState.Active -> {
                // Aktif abonelik
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(dimens.radiusLarge)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimens.space20),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(dimens.iconLg)
                        )
                        Spacer(modifier = Modifier.size(dimens.space12))
                        Column {
                            Text(
                                text = stringResource(R.string.billing_active_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.billing_active_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is SubscriptionState.Loading -> {
                SubscriptionLoadingSkeleton()
            }

            is SubscriptionState.Error -> {
                val resolvedMessage = when (uiState.subscriptionState.message) {
                    BillingErrorKeys.CONNECTING -> stringResource(R.string.billing_error_connecting)
                    BillingErrorKeys.RECONNECTING -> stringResource(R.string.billing_error_reconnecting)
                    BillingErrorKeys.UNSUPPORTED -> stringResource(R.string.billing_error_unsupported)
                    else -> uiState.subscriptionState.message.orEmpty()
                }
                Text(
                    text = stringResource(
                        R.string.billing_error_prefix,
                        resolvedMessage
                    ),
                    color = colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(dimens.space16))
                PlansList(uiState.productDetails, onPlanSelected)
            }

            else -> {
                // Inactive veya Unknown → planları göster
                PlansList(uiState.productDetails, onPlanSelected)
            }
        }

        // Özellikler listesi
        Spacer(modifier = Modifier.height(dimens.space32))
        FeaturesList()
        Spacer(modifier = Modifier.height(dimens.space16))
    }
}

@Composable
private fun SubscriptionLoadingSkeleton() {
    val dimens = LocalDimens.current
    val shimmer = rememberSkeletonBrush()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.space12)
    ) {
        Box(
            modifier = Modifier
                .size(dimens.iconXl)
                .clip(CircleShape)
                .background(shimmer)
                .align(Alignment.CenterHorizontally)
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(dimens.space24)
                .align(Alignment.CenterHorizontally),
            brush = shimmer
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            brush = shimmer
        )
        repeat(3) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                brush = shimmer
            )
        }
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.buttonHeight + dimens.space6),
            brush = shimmer
        )
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    brush: Brush
) {
    val dimens = LocalDimens.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(brush)
    )
}

@Composable
private fun rememberSkeletonBrush(): Brush {
    val motion = LocalMotion.current
    val transition = rememberInfiniteTransition(label = "subscription_skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = motion.durationSlow * 4,
                easing = motion.emphasizedEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "subscription_skeleton_translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.55f),
            base.copy(alpha = 0.9f),
            base.copy(alpha = 0.55f)
        ),
        start = Offset(translate - 400f, 0f),
        end = Offset(translate, 240f)
    )
}

@Composable
private fun PlansList(
    productDetails: List<BillingProduct>,
    onPlanSelected: (BillingProduct) -> Unit
) {
    val dimens = LocalDimens.current

    if (productDetails.isEmpty()) {
        Text(
            text = stringResource(R.string.billing_plans_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    var selectedPackageId by remember { mutableStateOf<String?>(null) }

    productDetails.forEach { productPackage ->
        val isSelected = selectedPackageId == productPackage.id
        val planLabel = planLabelFor(productPackage)
        val priceText = productPackage.priceText

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.space4)
                .border(
                    width = if (isSelected) dimens.space2 + dimens.strokeThin else dimens.stroke,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                    shape = RoundedCornerShape(dimens.radiusMedium)
                )
                .clickable { selectedPackageId = productPackage.id },
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    app_transparent
                }
            ),
            shape = RoundedCornerShape(dimens.radiusMedium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.space16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = planLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Box(contentAlignment = Alignment.CenterEnd) {
                    Text(
                        text = priceText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Text(
                            text = stringResource(R.string.billing_selected_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = dimens.space20 + dimens.space2)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(dimens.space16))

    AppButton(
        text = stringResource(R.string.billing_subscribe),
        onClick = {
            val selectedPackage = productDetails.firstOrNull { it.id == selectedPackageId }
            if (selectedPackage != null) {
                onPlanSelected(selectedPackage)
            }
        },
        enabled = selectedPackageId != null,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.buttonHeight + dimens.space6),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(dimens.radiusMedium)
    ) {
        Text(
            text = stringResource(R.string.billing_subscribe),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }

    Spacer(modifier = Modifier.height(dimens.space16))
    SubscriptionDisclosureCard()
}

@Composable
private fun FeaturesList() {
    val dimens = LocalDimens.current

    val features = listOf(
        stringResource(R.string.billing_feature_no_ads),
        stringResource(R.string.billing_feature_continuous),
        stringResource(R.string.billing_feature_no_open_ad),
        stringResource(R.string.billing_feature_cancel_anytime),
    )

    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.space8)
    ) {
        features.forEach { feature ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = dimens.space8)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(dimens.iconXs)
                )
                Spacer(modifier = Modifier.size(dimens.space8))
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubscriptionDisclosureCard() {
    val dimens = LocalDimens.current
    val colorScheme = MaterialTheme.colorScheme

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(dimens.radiusMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.space16),
            verticalArrangement = Arrangement.spacedBy(dimens.space8)
        ) {
            Text(
                text = stringResource(R.string.billing_disclosure_title),
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.billing_disclosure_body),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.billing_disclosure_footer),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun planLabelFor(product: BillingProduct): String {
    fun keyFromBasePlanId(): String? = product.basePlanId?.trim()?.lowercase()

    return when (product.billingPeriod) {
        "P1D" -> stringResource(R.string.billing_plan_daily)
        "P1W" -> stringResource(R.string.billing_plan_weekly)
        "P1M" -> stringResource(R.string.billing_plan_monthly)
        "P2M" -> stringResource(R.string.billing_plan_2_months)
        "P3M" -> stringResource(R.string.billing_plan_3_months)
        "P6M" -> stringResource(R.string.billing_plan_6_months)
        "P1Y" -> stringResource(R.string.billing_plan_yearly)
        else -> {
            when (keyFromBasePlanId()) {
                "gunluk", "daily" -> stringResource(R.string.billing_plan_daily)
                "haftalik", "weekly" -> stringResource(R.string.billing_plan_weekly)
                "aylik", "monthly" -> stringResource(R.string.billing_plan_monthly)
                "yillik", "yearly", "annual" -> stringResource(R.string.billing_plan_yearly)
                else -> stringResource(R.string.billing_plan_generic)
            }
        }
    }
}

@Preview(showBackground = true, name = "Subscription Loading")
@Composable
private fun SubscriptionScreenLoadingPreview() {
    AppTheme(flavorName = "namazvakitleri") {
        SubscriptionScreen(
            uiState = SubscriptionUiState(subscriptionState = SubscriptionState.Loading),
            onPlanSelected = {}
        )
    }
}

@Preview(showBackground = true, name = "Subscription Active")
@Composable
private fun SubscriptionScreenActivePreview() {
    AppTheme(flavorName = "namazvakitleri") {
        SubscriptionScreen(
            uiState = SubscriptionUiState(
                subscriptionState = SubscriptionState.Active(
                    expiryDate = null,
                    isAutoRenewing = true
                )
            ),
            onPlanSelected = {}
        )
    }
}
