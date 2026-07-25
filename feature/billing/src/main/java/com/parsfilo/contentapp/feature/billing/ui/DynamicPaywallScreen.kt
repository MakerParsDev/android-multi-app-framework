package com.parsfilo.contentapp.feature.billing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DynamicPaywallScreen(
    variantKey: String = "default_annual_highlight",
    onSubscribeClicked: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Unlock Premium Access",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Active Variant: $variantKey",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(
            onClick = onSubscribeClicked,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Start Premium Trial")
        }
    }
}
