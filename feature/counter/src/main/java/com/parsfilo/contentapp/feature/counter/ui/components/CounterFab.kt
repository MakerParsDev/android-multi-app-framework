package com.parsfilo.contentapp.feature.counter.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parsfilo.contentapp.feature.counter.R

@Composable
fun CounterFab(
    arabicText: String,
    latinText: String,
    currentCount: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressing by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressing) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "counter_fab_scale",
    )

    val pulseAlpha by rememberInfiniteTransition(label = "counter_fab_pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1500)),
        label = "counter_fab_pulse_alpha",
    )

    val pulseColor = MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha)
    val onSecondary = MaterialTheme.colorScheme.onSecondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(220.dp)
            .minimumInteractiveComponentSize()
            .semantics {
                if (!contentDescription.isNullOrBlank()) {
                    this.contentDescription = contentDescription
                }
            },
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            drawCircle(
                color = pulseColor,
                radius = size.minDimension / 2f,
            )
        }

        Surface(
            modifier = Modifier
                .size(200.dp)
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap,
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondary,
            shadowElevation = 12.dp,
            tonalElevation = 8.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = arabicText,
                        fontSize = 22.sp,
                        color = onSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = latinText,
                    fontSize = 13.sp,
                    color = onSecondary.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = stringResource(R.string.counter_tap_hint),
                    fontSize = 12.sp,
                    color = onSecondary.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = currentCount.toString(),
                    fontSize = 14.sp,
                    color = onSecondary,
                )
            }
        }
    }
}
