package com.parsfilo.contentapp.feature.quran.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parsfilo.contentapp.feature.quran.config.QuranApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciterSelectionSheet(
    reciters: List<QuranApiConfig.Reciters.ReciterInfo>,
    selectedReciterId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reciters.forEach { reciter ->
                val text = if (reciter.id == selectedReciterId) {
                    "${reciter.displayName} ✓"
                } else {
                    reciter.displayName
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(reciter.id)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
