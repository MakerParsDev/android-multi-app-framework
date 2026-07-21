package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.core.designsystem.component.AppCard
import com.parsfilo.contentapp.core.designsystem.component.AppTextButton
import com.parsfilo.contentapp.core.designsystem.tokens.LocalDimens
import com.parsfilo.contentapp.feature.counter.R
import com.parsfilo.contentapp.feature.counter.model.ZikirItem

@Composable
fun ZikirTextCard(
    zikir: ZikirItem,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDimens.current

    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(d.radiusXLarge),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = zikir.latinText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                AppTextButton(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_change_zikir),
                    onClick = onChangeClick,
                )
            }

            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = zikir.arabicText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = zikir.turkishMeaning,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )

            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.20f),
            )

            Text(
                text = androidx.compose.ui.res.stringResource(R.string.counter_virtue_label),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = zikir.virtue,
                fontSize = 13.sp,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.counter_virtue_source_label),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(d.space4))
                Text(
                    text = zikir.virtueSource,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                )
            }
        }
    }
}
