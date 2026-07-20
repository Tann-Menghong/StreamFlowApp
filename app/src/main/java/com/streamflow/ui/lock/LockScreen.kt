package com.streamflow.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shown over everything while the app is locked (Settings > App lock). The real
// authentication is the system fingerprint/PIN sheet launched by MainActivity;
// this is the fallback surface with a manual "Unlock" retry.
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                Modifier.size(72.dp).background(
                    MaterialTheme.colorScheme.primary.copy(0.12f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp))
            }
            Text("StreamFlow is locked", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Unlock with your fingerprint or PIN to continue",
                fontSize = 13.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onUnlock, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}
