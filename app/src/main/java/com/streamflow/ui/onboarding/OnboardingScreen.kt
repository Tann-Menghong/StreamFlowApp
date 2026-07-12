package com.streamflow.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamflow.data.local.AppPreferences
import kotlinx.coroutines.launch

// First-launch setup: country -> interests -> theme, then straight to the feed
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(prefs: AppPreferences, onDone: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf("US") }
    val interests = remember { mutableStateListOf("Music", "Gaming", "News") }
    var theme by remember { mutableStateOf("DARK") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Brand header
        Row(
            Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(
                    Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                    CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("StreamFlow", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                when (page) {
                    0 -> {
                        Text("Where do you watch from?", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("Trending videos follow your country", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                        listOf("KH" to "Cambodia 🇰🇭", "US" to "United States", "TH" to "Thailand",
                            "VN" to "Vietnam", "KR" to "South Korea", "JP" to "Japan", "IN" to "India").forEach { (code, name) ->
                            Surface(
                                onClick = { country = code },
                                shape = RoundedCornerShape(14.dp),
                                color = if (country == code) MaterialTheme.colorScheme.primary.copy(0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(name, fontSize = 14.sp,
                                    fontWeight = if (country == code) FontWeight.Bold else FontWeight.Normal,
                                    color = if (country == code) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                            }
                        }
                    }
                    1 -> {
                        Text("What do you like?", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("Pick a few topics for your home feed", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                        listOf("Music", "Gaming", "Sports", "News", "Tech", "Comedy", "Film",
                            "Cooking", "Travel", "Education", "K-Pop", "Khmer News").chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)) {
                                row.forEach { cat ->
                                    val on = cat in interests
                                    FilterChip(
                                        selected = on,
                                        onClick = { if (on) interests.remove(cat) else interests.add(cat) },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(16.dp),
                                        border = null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Pick your look", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text("You can change everything later in Settings", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                        listOf("DARK" to "Dark (recommended)", "AMOLED" to "AMOLED black", "LIGHT" to "Light").forEach { (code, name) ->
                            Surface(
                                onClick = { theme = code },
                                shape = RoundedCornerShape(14.dp),
                                color = if (theme == code) MaterialTheme.colorScheme.primary.copy(0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(name, fontSize = 14.sp,
                                    fontWeight = if (theme == code) FontWeight.Bold else FontWeight.Normal,
                                    color = if (theme == code) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                            }
                        }
                    }
                }
            }
        }

        // Dots + next/finish
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                Box(Modifier.padding(end = 6.dp).size(if (pager.currentPage == i) 10.dp else 7.dp)
                    .background(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(0.4f), CircleShape))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (pager.currentPage < 2) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    } else {
                        scope.launch {
                            prefs.setCountry(country)
                            if (interests.isNotEmpty()) prefs.setHomeCategories(interests.toList())
                            prefs.setTheme(theme)
                            prefs.setOnboardingDone(true)
                            onDone()
                        }
                    }
                },
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(if (pager.currentPage < 2) "Next" else "Start watching",
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
