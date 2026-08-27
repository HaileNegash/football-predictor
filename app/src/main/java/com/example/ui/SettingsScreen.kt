package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.AccentColorMode
import com.example.models.OddsFormat
import com.example.models.ThemeMode
import com.example.models.UserTier
import com.example.viewmodel.CloudSyncState
import com.example.viewmodel.PredictorViewModel
import kotlinx.coroutines.launch

private val CardBorderColor = Color(0xFF262A36)
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)

enum class SettingsCategory(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.Settings),
    APPEARANCE("Appearance", Icons.Filled.Palette),
    PREFERENCES("Preferences", Icons.Filled.SportsSoccer),
    CLOUD_DASHBOARD("Cloud & AI", Icons.Filled.CloudSync),
    SYSTEM("System", Icons.Filled.Storage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val lastCloudSyncTimestamp by viewModel.lastCloudSyncTimestamp.collectAsStateWithLifecycle()
    val savedSlips by viewModel.savedSlipsHistory.collectAsStateWithLifecycle()
    val keysByRole by viewModel.keyManager.keysByRole.collectAsStateWithLifecycle()
    val keySyncStatus by viewModel.keyManager.lastSyncStatus.collectAsStateWithLifecycle()
    val allLoadedKeys = keysByRole.values.flatten()
    val activeAccent = customSettings.accentColorMode.color
    val activeBg = customSettings.themeMode.bgColor
    val activeCardBg = customSettings.themeMode.cardColor

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCategory by remember { mutableStateOf(SettingsCategory.ALL) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Settings & Config",
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = activeAccent
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.keyManager.triggerCloudSync()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Synchronizing settings & dashboard config...")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = activeAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeBg,
                    titleContentColor = TextMain
                )
            )
        },
        containerColor = activeBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Filter Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SettingsCategory.entries) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            onClick = { selectedCategory = category },
                            color = if (isSelected) activeAccent else Color(0xFF1B202B),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) activeAccent else CardBorderColor
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else TextSub,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.Black else TextMain,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // 0. Account & Identity Header Card (Always visible or in ALL)
            if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.SYSTEM) {
                item {
                    Surface(
                        onClick = onNavigateToAuth,
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else CardBorderColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else activeAccent,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser.displayName.take(1).uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = Color.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentUser.displayName,
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        if (currentUser.tier == UserTier.PRO_VIP) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFFFD600).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "★ VIP",
                                                    color = Color(0xFFFFD600),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (currentUser.email == "guest@footballpredictor.app") "Tap to sign in with Google" else currentUser.email,
                                        color = TextSub,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Surface(
                                color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600).copy(alpha = 0.2f) else Color(0xFF1E222B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else CardBorderColor
                                )
                            ) {
                                Text(
                                    text = if (currentUser.tier == UserTier.PRO_VIP) "UNLIMITED" else "${currentUser.remainingPredictions} Free Left",
                                    color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else activeAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. Theme & Appearance
            if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.APPEARANCE) {
                item {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Palette,
                        title = "THEME & VISUAL STYLING",
                        accentColor = activeAccent
                    )
                }

                item {
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Canvas Background Tone",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Text(
                                text = "Select an eye-safe dark theme tailored for match analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSub
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Theme Mode Cards
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemeMode.entries.forEach { theme ->
                                    val isSelected = customSettings.themeMode == theme
                                    Surface(
                                        onClick = { viewModel.updateThemeMode(theme) },
                                        color = if (isSelected) activeAccent.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.5.dp,
                                            if (isSelected) activeAccent else CardBorderColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(theme.previewIcon, fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = theme.title,
                                                        color = if (isSelected) activeAccent else TextMain,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = theme.description,
                                                        color = TextSub,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(activeAccent, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Accent Color Palette
                            Text(
                                text = "Accent Highlight Color",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(AccentColorMode.entries) { accent ->
                                    val isSelected = customSettings.accentColorMode == accent
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.updateAccentColor(accent) }
                                            .padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(accent.color, CircleShape)
                                                .then(
                                                    if (isSelected) Modifier.padding(2.dp) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = accent.title.substringBefore(" "),
                                            color = if (isSelected) accent.color else TextSub,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Football & Betting Preferences
            if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.PREFERENCES) {
                item {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Tune,
                        title = "ODDS & MATCH CONFIGURATION",
                        accentColor = activeAccent
                    )
                }

                item {
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Odds Display Format",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Text(
                                text = "Choose how odds multipliers are calculated and formatted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSub
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OddsFormat.entries.forEach { format ->
                                    val isSelected = customSettings.oddsFormat == format
                                    Surface(
                                        onClick = { viewModel.updateOddsFormat(format) },
                                        color = if (isSelected) activeAccent else Color(0xFF1E222B),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) activeAccent else CardBorderColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = format.title.substringBefore(" "),
                                                color = if (isSelected) Color.Black else TextMain,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = format.example,
                                                color = if (isSelected) Color.Black.copy(alpha = 0.8f) else TextSub,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            // Show Finished Matches Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Visibility,
                                                contentDescription = null,
                                                tint = activeAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Show Finished Matches",
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Keep completed games visible in fixture list",
                                            color = TextSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = customSettings.showFinishedMatches,
                                    onCheckedChange = { viewModel.toggleShowFinished(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = activeAccent
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Haptic Feedback Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Vibration,
                                                contentDescription = null,
                                                tint = activeAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Tactile Haptics",
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Vibrate lightly when predicting and selecting matches",
                                            color = TextSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = customSettings.hapticsEnabled,
                                    onCheckedChange = { viewModel.toggleHaptics(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = activeAccent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 3. Central Web Dashboard & Cloud Synchronization
            if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.CLOUD_DASHBOARD) {
                item {
                    SettingsSectionHeader(
                        icon = Icons.Filled.CloudSync,
                        title = "REMOTE DASHBOARD & CLOUD VAULT",
                        accentColor = activeAccent
                    )
                }

                item {
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Dashboard Status Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🌐", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Dashboard Configuration",
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Real-time Firestore Vault Sync",
                                            color = TextSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Surface(
                                    color = when (cloudSyncState) {
                                        CloudSyncState.SYNCED -> Color(0xFF00E676).copy(alpha = 0.15f)
                                        CloudSyncState.SYNCING -> activeAccent.copy(alpha = 0.15f)
                                        else -> Color(0xFF262A36)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        when (cloudSyncState) {
                                            CloudSyncState.SYNCED -> Color(0xFF00E676)
                                            CloudSyncState.SYNCING -> activeAccent
                                            else -> Color.Transparent
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (cloudSyncState == CloudSyncState.SYNCING) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(10.dp),
                                                color = activeAccent,
                                                strokeWidth = 1.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = cloudSyncState.label,
                                            color = when (cloudSyncState) {
                                                CloudSyncState.SYNCED -> Color(0xFF00E676)
                                                CloudSyncState.SYNCING -> activeAccent
                                                else -> TextSub
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "All neural models, API providers, and active brain settings are managed strictly from your Web Dashboard. Any updates apply instantly via Firestore.",
                                color = TextSub,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Active Dashboard Config Overview Card
                            Surface(
                                color = Color(0xFF141822),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "DASHBOARD ACTIVE MODEL",
                                            color = TextSub,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Surface(
                                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "DASHBOARD CONTROLLED",
                                                color = Color(0xFF00E676),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🧠 ", fontSize = 16.sp)
                                        Text(
                                            text = viewModel.keyManager.activeBrainModel,
                                            color = activeAccent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sync & Backup Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.syncDashboardAndToolsToFirestore { _, msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_sync_dashboard_to_cloud")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Push Slips", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.keyManager.triggerCloudSync()
                                        viewModel.syncFromFirebaseCloud { _, msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262A36)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_restore_from_cloud")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = TextMain
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pull Vault", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Synced Keys Display List
                            Text(
                                text = "Synced Provider Keys (${allLoadedKeys.size})",
                                color = TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (allLoadedKeys.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E222B), RoundedCornerShape(10.dp))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = activeAccent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = keySyncStatus ?: "Receiving dashboard configuration...",
                                            color = TextSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    allLoadedKeys.forEach { managedKey ->
                                        val isRoleActive = managedKey.status == "ACTIVE"
                                        val isCurrentRoleKey = viewModel.keyManager.getActiveManagedKey(managedKey.apiRole)?.id == managedKey.id

                                        Surface(
                                            color = if (isCurrentRoleKey) activeAccent.copy(alpha = 0.08f) else Color(0xFF1E222B),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrentRoleKey) activeAccent.copy(alpha = 0.5f) else CardBorderColor
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(managedKey.apiRole.iconEmoji, fontSize = 16.sp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    text = managedKey.label,
                                                                    color = TextMain,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp
                                                                )
                                                                if (isCurrentRoleKey) {
                                                                    Spacer(modifier = Modifier.width(6.dp))
                                                                    Surface(
                                                                        color = activeAccent,
                                                                        shape = RoundedCornerShape(4.dp)
                                                                    ) {
                                                                        Text(
                                                                            text = "ACTIVE",
                                                                            color = Color.Black,
                                                                            fontWeight = FontWeight.Black,
                                                                            fontSize = 9.sp,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            Text(
                                                                text = "${managedKey.role} • ${managedKey.maskedKey}",
                                                                color = TextSub,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        color = when {
                                                            managedKey.isCoolingDown -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                                            isRoleActive -> Color(0xFF00E676).copy(alpha = 0.2f)
                                                            else -> Color(0xFFFF5252).copy(alpha = 0.2f)
                                                        },
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (managedKey.isCoolingDown) "COOLDOWN" else managedKey.status,
                                                            color = when {
                                                                managedKey.isCoolingDown -> Color(0xFFFF9800)
                                                                isRoleActive -> Color(0xFF00E676)
                                                                else -> Color(0xFFFF5252)
                                                            },
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                if (managedKey.endpointUrl.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "ENDPOINT: ${managedKey.endpointUrl}",
                                                        color = TextSub,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Storage & Diagnostics
            if (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.SYSTEM) {
                item {
                    SettingsSectionHeader(
                        icon = Icons.Filled.Storage,
                        title = "STORAGE & SYSTEM DIAGNOSTICS",
                        accentColor = activeAccent
                    )
                }

                item {
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Clear Match Cache Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = Color(0xFFFF5252).copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = null,
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Clear Match Cache",
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Force refresh all cached fixtures & predictions",
                                            color = TextSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick = { showClearCacheDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262A36)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Clear", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // App Version & Diagnostics Info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("App Version", color = TextSub, fontSize = 11.sp)
                                    Text("2.5.0 (Note 3 / Android 5.0+ Cloud Ready)", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Surface(
                                    color = activeAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "STABLE",
                                        color = activeAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = activeCardBg,
            title = {
                Text("Clear Match Cache?", color = TextMain, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will wipe locally cached football matches and fetch the latest live data from configured providers.",
                    color = TextSub
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllMatchCache()
                        showClearCacheDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Match cache cleared. Fresh data loaded.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Clear Cache", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel", color = TextSub)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 1.sp
        )
    }
}
