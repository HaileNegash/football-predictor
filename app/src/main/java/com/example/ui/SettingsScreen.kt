package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.keymanager.ApiRole
import com.example.keymanager.KeyStatus
import com.example.keymanager.ManagedApiKey
import com.example.models.AccentColorMode
import com.example.models.AiReasoningDepth
import com.example.models.OddsFormat
import com.example.models.RiskTolerance
import com.example.models.ThemeMode
import com.example.models.UserAiModel
import com.example.models.UserTier
import com.example.viewmodel.PredictorViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBorderColor = Color(0xFF262A36)
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)

enum class SettingsCategory(val title: String, val icon: ImageVector, val tag: String) {
    AI_STRATEGY("AI Brain", Icons.Filled.Psychology, "AI & Strategy"),
    API_VAULT("API Keys", Icons.Filled.Key, "API Keys & Vault"),
    BETTING("Betting", Icons.Filled.SportsSoccer, "Match & Odds"),
    APPEARANCE("Theme", Icons.Filled.Palette, "Appearance"),
    STORAGE("Storage", Icons.Filled.Storage, "Storage & Data")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val keysByRole by viewModel.keyManager.keysByRole.collectAsStateWithLifecycle()
    val isCloudSyncEnabled by viewModel.keyManager.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val isSyncingKeys by viewModel.keyManager.isSyncing.collectAsStateWithLifecycle()
    val lastKeySyncStatus by viewModel.keyManager.lastSyncStatus.collectAsStateWithLifecycle()
    val savedSlips by viewModel.savedSlipsHistory.collectAsStateWithLifecycle()
    val selectedBetTypes by viewModel.selectedBetTypes.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val userAddedModels by viewModel.userAddedModels.collectAsStateWithLifecycle()

    val activeAccent = customSettings.accentColorMode.color
    val activeBg = customSettings.themeMode.bgColor
    val activeCardBg = customSettings.themeMode.cardColor

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeCategory by remember { mutableStateOf(SettingsCategory.AI_STRATEGY) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog States
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ManagedApiKey?>(null) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var showExportImportDialog by remember { mutableStateOf(false) }
    var showExportSlipsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isTestingKeyId by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Copied $label to clipboard")
        }
    }

    Scaffold(
        containerColor = activeBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings & Config",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Text(
                            text = if (isCloudSyncEnabled) "Firebase Cloud Vault • Auto-Synced" else "Local Storage • Vault Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = activeAccent
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
                            tint = TextMain
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportImportDialog = true },
                        modifier = Modifier.testTag("btn_settings_export_import")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Backup Config",
                            tint = TextSub
                        )
                    }
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.testTag("btn_settings_reset_all")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = "Reset Defaults",
                            tint = TextSub
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeBg
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Tabs Row
            Surface(
                color = activeBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SettingsCategory.entries) { category ->
                        val isSelected = activeCategory == category
                        Surface(
                            onClick = { activeCategory = category },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) activeAccent else activeCardBg,
                            border = BorderStroke(1.dp, if (isSelected) activeAccent else CardBorderColor),
                            modifier = Modifier.testTag("tab_settings_${category.name.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else TextSub,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.White else TextMain,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = CardBorderColor, thickness = 1.dp)

            // Main Settings Content
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (activeCategory) {
                    SettingsCategory.AI_STRATEGY -> {
                        // 1. ACTIVE AI MODEL SELECTOR (Configured Models Selection)
                        item {
                            val keysFromVault = keysByRole.values.flatten().filter { it.status == "ACTIVE" && it.modelName.isNotBlank() }.map { key ->
                                UserAiModel(
                                    id = key.modelName,
                                    name = key.modelName.split("/").last().replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                    provider = key.label.ifBlank { "API Vault" },
                                    endpointUrl = key.endpointUrl,
                                    apiKey = key.key,
                                    badge = "Vault Key",
                                    description = "Configured via ${key.label}"
                                )
                            }
                            val allConfiguredModels = (userAddedModels + keysFromVault).distinctBy { it.id }
                            val activeModelName = allConfiguredModels.find { it.id == customSettings.activeAiModelId }?.name
                                ?: customSettings.activeAiModelId.split("/").last().ifBlank { "Auto" }

                            SettingsCard(
                                title = "AI Model Engine",
                                subtitle = "Select configured intelligence model for match predictions",
                                icon = Icons.Filled.Psychology,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = true,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = activeModelName,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            ) {
                                if (allConfiguredModels.isEmpty()) {
                                    Surface(
                                        color = Color(0xFF191C24),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF262A36)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "No Configured AI Models",
                                                color = TextMain,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Add and configure your AI model in the API Keys vault tab to select it here.",
                                                color = TextSub,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        allConfiguredModels.forEach { model ->
                                            val isSelected = customSettings.activeAiModelId == model.id
                                            Surface(
                                                onClick = { viewModel.updateActiveAiModel(model.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) activeAccent.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) activeAccent else Color(0xFF262A36)
                                                ),
                                                modifier = Modifier.fillMaxWidth().testTag("ai_model_card_${model.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                                        contentDescription = if (isSelected) "Selected" else "Unselected",
                                                        tint = if (isSelected) activeAccent else TextSub,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = model.name,
                                                                color = if (isSelected) activeAccent else TextMain,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = if (isSelected) activeAccent.copy(alpha = 0.3f) else Color(0xFF2A2E3D)
                                                            ) {
                                                                Text(
                                                                    text = model.badge,
                                                                    color = if (isSelected) activeAccent else TextSub,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            if (model.provider.isNotBlank()) {
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = Color(0xFF161920)
                                                                ) {
                                                                    Text(
                                                                        text = model.provider,
                                                                        color = TextSub,
                                                                        fontSize = 9.sp,
                                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "ID: ${model.id}",
                                                            color = TextSub,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        if (model.description.isNotBlank()) {
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = model.description,
                                                                color = TextSub.copy(alpha = 0.8f),
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
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

                        // 2. REASONING DEPTH
                        item {
                            SettingsCard(
                                title = "Reasoning Depth Strategy",
                                subtitle = "Select mathematical complexity of calculation",
                                icon = Icons.Filled.Tune,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = customSettings.aiReasoningDepth.title,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AiReasoningDepth.entries.forEach { depth ->
                                        val isSelected = customSettings.aiReasoningDepth == depth
                                        Surface(
                                            onClick = { viewModel.updateAiReasoningDepth(depth) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) activeAccent.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF262A36)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = depth.iconEmoji, fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = depth.title,
                                                        color = if (isSelected) activeAccent else TextMain,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = depth.subtitle,
                                                        color = TextSub,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. RISK TOLERANCE
                        item {
                            SettingsCard(
                                title = "Risk Stance Profile",
                                subtitle = "Balances safety probabilities vs high payout multipliers",
                                icon = Icons.Filled.AutoAwesome,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = customSettings.riskTolerance.title,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RiskTolerance.entries.forEach { risk ->
                                        val isSelected = customSettings.riskTolerance == risk
                                        Surface(
                                            onClick = { viewModel.updateRiskTolerance(risk) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) activeAccent.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF262A36)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = risk.title,
                                                            color = if (isSelected) activeAccent else TextMain,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = risk.badge,
                                                            color = activeAccent,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = risk.subtitle,
                                                        color = TextSub,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. MIN CONFIDENCE THRESHOLD
                        item {
                            SettingsCard(
                                title = "Minimum Confidence Threshold",
                                subtitle = "Filter predictions below this certainty index",
                                icon = Icons.Filled.Tune,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${customSettings.minConfidenceThreshold}%",
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Certainty Floor", color = TextSub, fontSize = 12.sp)
                                        Surface(
                                            color = activeAccent.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${customSettings.minConfidenceThreshold}%",
                                                color = activeAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Slider(
                                        value = customSettings.minConfidenceThreshold.toFloat(),
                                        onValueChange = { viewModel.updateMinConfidence(it.toInt()) },
                                        valueRange = 50f..95f,
                                        steps = 44,
                                        colors = SliderDefaults.colors(
                                            thumbColor = activeAccent,
                                            activeTrackColor = activeAccent,
                                            inactiveTrackColor = Color(0xFF2E313C)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("50% (Loose)", color = TextSub, fontSize = 10.sp)
                                        Text("75% (Balanced)", color = TextSub, fontSize = 10.sp)
                                        Text("95% (Ironclad)", color = TextSub, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        // 5. CUSTOM TACTICAL PROMPT TUNING
                        item {
                            SettingsCard(
                                title = "Tactical Prompt Directives",
                                subtitle = "Inject custom instruction matrices for AI analysis",
                                icon = Icons.Filled.Edit,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = if (customSettings.customTacticalPrompt.isNotBlank()) activeAccent.copy(alpha = 0.15f) else Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (customSettings.customTacticalPrompt.isNotBlank()) "Active" else "Default",
                                            color = if (customSettings.customTacticalPrompt.isNotBlank()) activeAccent else TextSub,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column {
                                    if (customSettings.customTacticalPrompt.isNotBlank()) {
                                        Surface(
                                            color = Color(0xFF1E222B),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF262A36)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = customSettings.customTacticalPrompt,
                                                color = TextMain,
                                                fontSize = 11.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Button(
                                        onClick = { showCustomPromptDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (customSettings.customTacticalPrompt.isBlank()) "Add Custom Tactical Instructions" else "Edit Tactical Prompt",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.API_VAULT -> {
                        // 0. FIREBASE CLOUD SYNC STATUS & CONTROLS
                        item {
                            SettingsCard(
                                title = "Firebase Firestore Cloud Keys",
                                subtitle = "Securely synchronize API keys with your Firebase account",
                                icon = Icons.Filled.Refresh,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = if (isCloudSyncEnabled) activeAccent.copy(alpha = 0.15f) else Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isCloudSyncEnabled) "Cloud Sync" else "Local Only",
                                            color = if (isCloudSyncEnabled) activeAccent else TextSub,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Firebase Cloud Storage",
                                                color = TextMain,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (isCloudSyncEnabled) "Enabled • Keys saved to Firestore" else "Disabled • Storing keys locally on-device",
                                                color = TextSub,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = isCloudSyncEnabled,
                                            onCheckedChange = { enabled ->
                                                viewModel.keyManager.setCloudSyncEnabled(enabled)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (enabled) "Enabled Firebase Cloud Storage for keys" else "Switched keys storage to local on-device mode"
                                                    )
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = activeAccent
                                            )
                                        )
                                    }

                                    // Firebase Connection Details Information Card
                                    Surface(
                                        color = Color(0xFF141720),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF262A38)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Connected Firebase Project:", color = TextSub, fontSize = 11.sp)
                                                Text("ai-football-predictor-3daad", color = activeAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Firestore Cloud Path:", color = TextSub, fontSize = 11.sp)
                                                Text("users/${currentUser.userId}/api_keys", color = TextMain, fontSize = 11.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Security & Storage:", color = TextSub, fontSize = 11.sp)
                                                Text("Encrypted Local Vault + Firestore", color = Color(0xFF4CAF50), fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    if (lastKeySyncStatus != null) {
                                        Surface(
                                            color = Color(0xFF161920),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, CardBorderColor),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = lastKeySyncStatus ?: "",
                                                        color = TextSub,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isSyncingKeys) {
                                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = activeAccent, strokeWidth = 2.dp)
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.keyManager.syncWithFirestore(currentUser.userId) { success, msg ->
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                }
                                            },
                                            enabled = !isSyncingKeys,
                                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent.copy(alpha = 0.2f)),
                                            border = BorderStroke(1.dp, activeAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                tint = activeAccent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isSyncingKeys) "Syncing..." else "Sync Cloud Keys",
                                                color = activeAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = onNavigateToAuth,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, CardBorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = null,
                                                tint = TextSub,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("User Account", color = TextMain, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // API KEYS VAULT
                        item {
                            val totalKeys = keysByRole.values.sumOf { it.size }
                            SettingsCard(
                                title = "API Vault & Data Providers",
                                subtitle = "Add Football & AI API keys for live data & high-quota predictions",
                                icon = Icons.Filled.Key,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = true,
                                summaryBadge = {
                                    Surface(
                                        color = if (totalKeys > 0) activeAccent.copy(alpha = 0.15f) else Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$totalKeys keys",
                                            color = if (totalKeys > 0) activeAccent else TextSub,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            editingKey = null
                                            showAddKeyDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add New API Key", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    // Quick Provider Registration & Free Key Links
                                    Surface(
                                        color = Color(0xFF161920),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CardBorderColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Link,
                                                    contentDescription = null,
                                                    tint = activeAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Get Free API Keys & Documentation",
                                                    color = TextMain,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "Tap any provider below to register and copy your API key:",
                                                color = TextSub,
                                                fontSize = 10.sp
                                            )
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(ApiRole.entries.filter { it.portalUrl.isNotBlank() }) { r ->
                                                    Surface(
                                                        onClick = { openWebUrl(context, r.portalUrl) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF1E222B),
                                                        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.35f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(text = r.iconEmoji, fontSize = 12.sp)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = r.displayName,
                                                                color = TextMain,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                contentDescription = "Open",
                                                                tint = activeAccent,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Display keys grouped by role
                                    ApiRole.entries.forEach { role ->
                                        val keys = keysByRole[role] ?: emptyList()
                                        RoleKeysSection(
                                            role = role,
                                            keys = keys,
                                            activeAccent = activeAccent,
                                            isTestingKeyId = isTestingKeyId,
                                            onTestKey = { keyItem ->
                                                isTestingKeyId = keyItem.id
                                                viewModel.keyManager.testKeyConnection(keyItem) { success, msg ->
                                                    isTestingKeyId = null
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                }
                                            },
                                            onEditKey = { keyItem ->
                                                editingKey = keyItem
                                                showAddKeyDialog = true
                                            },
                                            onDeleteKey = { keyItem ->
                                                viewModel.keyManager.removeKey(role, keyItem.id)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Removed key (${keyItem.maskedKey})")
                                                }
                                            },
                                            onResetStats = {
                                                viewModel.keyManager.resetKeyStats(role)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Reset statistics for ${role.displayName}")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.BETTING -> {
                        // 1. ODDS FORMAT
                        item {
                            SettingsCard(
                                title = "Odds Presentation Format",
                                subtitle = "Configure numeric representation of betting odds",
                                icon = Icons.Filled.SportsSoccer,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = customSettings.oddsFormat.title,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OddsFormat.entries.forEach { format ->
                                        val isSelected = customSettings.oddsFormat == format
                                        Surface(
                                            onClick = { viewModel.updateOddsFormat(format) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) activeAccent else Color(0xFF1E222B),
                                            border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF262A36)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = format.title,
                                                    color = if (isSelected) Color.White else TextMain,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = format.example,
                                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else TextSub,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 1.5. LIVE ODDS PROVIDER INTEGRATION
                        item {
                            val oddsKeys = keysByRole[ApiRole.THE_ODDS_API] ?: emptyList()
                            SettingsCard(
                                title = "Live Bookmaker Odds Feed",
                                subtitle = if (oddsKeys.isNotEmpty()) "The Odds API Connected (${oddsKeys.size} key active)" else "Connect The Odds API for real-time betting lines",
                                icon = Icons.Filled.SportsSoccer,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = if (oddsKeys.isNotEmpty()) activeAccent.copy(alpha = 0.15f) else Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (oddsKeys.isNotEmpty()) "Connected" else "Not Configured",
                                            color = if (oddsKeys.isNotEmpty()) activeAccent else TextSub,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "The Odds API provides live bookmaker market odds, 1X2 lines, spreads, and Over/Under totals directly into match simulations.",
                                        color = TextSub,
                                        fontSize = 11.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            onClick = { openWebUrl(context, ApiRole.THE_ODDS_API.portalUrl) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = activeAccent.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, activeAccent),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                    contentDescription = null,
                                                    tint = activeAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Get Free Key (the-odds-api.com)",
                                                    color = activeAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (oddsKeys.isEmpty()) {
                                            Button(
                                                onClick = {
                                                    editingKey = null
                                                    showAddKeyDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Add Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. BETTING CURRENCY & STAKE LIMITS
                        item {
                            SettingsCard(
                                title = "Currency & Default Stake",
                                subtitle = "Currency: ${selectedCurrency.name} (${selectedCurrency.symbol}) | Default Stake: ${selectedCurrency.symbol}${budget.toInt()}",
                                icon = Icons.Filled.SportsSoccer,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${selectedCurrency.symbol} (${selectedCurrency.code})",
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Select Currency", color = TextSub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(viewModel.availableCurrencies) { curr ->
                                            val isSelected = selectedCurrency.code == curr.code
                                            Surface(
                                                onClick = { viewModel.selectCurrency(curr) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) activeAccent else Color(0xFF1E222B),
                                                border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF262A36))
                                            ) {
                                                Text(
                                                    text = "${curr.symbol} ${curr.code}",
                                                    color = if (isSelected) Color.White else TextMain,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. ENABLED BET TYPES
                        item {
                            SettingsCard(
                                title = "Active Prediction Markets",
                                subtitle = "${selectedBetTypes.size} of ${viewModel.availableBetTypes.size} markets active",
                                icon = Icons.Filled.Tune,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${selectedBetTypes.size}/${viewModel.availableBetTypes.size}",
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.selectAllBetTypes() },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, activeAccent),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Select All", color = activeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.deselectAllBetTypes() },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, CardBorderColor),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Clear All", color = TextSub, fontSize = 11.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    viewModel.availableBetTypes.forEach { betType ->
                                        val isChecked = selectedBetTypes.contains(betType)
                                        Surface(
                                            onClick = { viewModel.toggleBetType(betType) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isChecked) activeAccent.copy(alpha = 0.12f) else Color(0xFF1E222B),
                                            border = BorderStroke(1.dp, if (isChecked) activeAccent else Color(0xFF262A36)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = betType,
                                                    color = if (isChecked) TextMain else TextSub,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                if (isChecked) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. DISPLAY TOGGLES
                        item {
                            SettingsCard(
                                title = "Match Display Filters",
                                subtitle = "Configure match lists & refresh intervals",
                                icon = Icons.Filled.Visibility,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (customSettings.compactCardMode) "Compact" else "Standard",
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingsToggleRow(
                                        title = "Show Finished Matches",
                                        subtitle = "Keep concluded fixtures visible with full-time scores",
                                        checked = customSettings.showFinishedMatches,
                                        onCheckedChange = { viewModel.toggleShowFinished(it) },
                                        activeAccent = activeAccent
                                    )
                                    SettingsToggleRow(
                                        title = "Compact Card Mode",
                                        subtitle = "Fit more match rows per screen with dense layout",
                                        checked = customSettings.compactCardMode,
                                        onCheckedChange = { viewModel.toggleCompactMode(it) },
                                        activeAccent = activeAccent
                                    )
                                    SettingsToggleRow(
                                        title = "Haptic Tactile Feedback",
                                        subtitle = "Subtle vibration pulse on bet selection & actions",
                                        checked = customSettings.hapticsEnabled,
                                        onCheckedChange = { viewModel.toggleHaptics(it) },
                                        activeAccent = activeAccent
                                    )
                                }
                            }
                        }
                    }

                    SettingsCategory.APPEARANCE -> {
                        // 1. THEME PALETTE
                        item {
                            SettingsCard(
                                title = "Theme Matrix",
                                subtitle = "Select dark visual canvas for optimal battery & aesthetics",
                                icon = Icons.Filled.Palette,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = customSettings.themeMode.title,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ThemeMode.entries.forEach { theme ->
                                        val isSelected = customSettings.themeMode == theme
                                        Surface(
                                            onClick = { viewModel.updateThemeMode(theme) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) activeAccent.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                            border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF262A36)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = theme.previewIcon, fontSize = 20.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(theme.bgColor)
                                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = theme.title,
                                                        color = if (isSelected) activeAccent else TextMain,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = theme.description,
                                                        color = TextSub,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. ACCENT COLOR
                        item {
                            SettingsCard(
                                title = "Vibrant Accent Highlight",
                                subtitle = "Custom glow color for buttons, badges, and active odds",
                                icon = Icons.Filled.Palette,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(customSettings.accentColorMode.color)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = customSettings.accentColorMode.title.substringBefore(" "),
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(AccentColorMode.entries) { accentMode ->
                                        val isSelected = customSettings.accentColorMode == accentMode
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { viewModel.updateAccentColor(accentMode) }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(accentMode.color)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) Color.White else Color.Transparent,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = accentMode.title.substringBefore(" "),
                                                color = if (isSelected) Color.White else TextSub,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SettingsCategory.STORAGE -> {
                        // 1. LOCAL USER PROFILE & TIER
                        item {
                            SettingsCard(
                                title = "User Profile & VIP Tier",
                                subtitle = "100% on-device credentials & tier entitlements",
                                icon = Icons.Filled.WorkspacePremium,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = true,
                                summaryBadge = {
                                    Surface(
                                        color = if (currentUser.tier == UserTier.PRO_VIP) activeAccent else Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (currentUser.tier == UserTier.PRO_VIP) "PRO VIP" else "FREE",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(text = currentUser.displayName, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(text = currentUser.email, color = TextSub, fontSize = 12.sp)
                                        }
                                        Surface(
                                            color = if (currentUser.tier == UserTier.PRO_VIP) activeAccent else Color(0xFF262A36),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (currentUser.tier == UserTier.PRO_VIP) "PRO VIP (UNLIMITED)" else "FREE TIER",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            viewModel.userManager.toggleUserTier()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Switched to ${if (currentUser.tier == UserTier.FREE) "PRO VIP" else "FREE"} Tier")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFF262A36) else activeAccent
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (currentUser.tier == UserTier.PRO_VIP) "Switch to Free Tier (Quota Limited)" else "⚡ 1-Tap Unlock PRO VIP (Unlimited Predictions)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. SAVED SLIPS EXPORT
                        item {
                            SettingsCard(
                                title = "Saved Prediction Slips",
                                subtitle = "${savedSlips.size} prediction slips stored locally in vault",
                                icon = Icons.Filled.Storage,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = activeAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${savedSlips.size} slips",
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showExportSlipsDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export Slips Text", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.clearAllSlips()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Cleared prediction slips history")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Clear Slips", color = Color(0xFFFF5252), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. CACHE & SYSTEM ACTIONS
                        item {
                            SettingsCard(
                                title = "Vault Maintenance & Cache",
                                subtitle = "Purge cached fixtures or perform clean factory reset",
                                icon = Icons.Filled.RestartAlt,
                                activeAccent = activeAccent,
                                cardBg = activeCardBg,
                                initiallyExpanded = false,
                                summaryBadge = {
                                    Surface(
                                        color = Color(0xFF262A36),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Manage",
                                            color = TextSub,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.clearAllMatchCache()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Purged fixture cache & reloaded live matches")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E222B)),
                                        border = BorderStroke(1.dp, CardBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = activeAccent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Purge Fixtures Cache & Refresh", color = TextMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = { showResetConfirmDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Factory Reset Settings to Default", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== DIALOGS ====================

    // 1. ADD / EDIT API KEY DIALOG
    if (showAddKeyDialog) {
        var keyInput by remember { mutableStateOf(editingKey?.key ?: "") }
        var labelInput by remember { mutableStateOf(editingKey?.label ?: "") }
        var selectedRole by remember { mutableStateOf(editingKey?.apiRole ?: ApiRole.API_FOOTBALL) }
        var endpointInput by remember { mutableStateOf(editingKey?.endpointUrl ?: "https://api.openai.com/v1/") }
        var modelNameInput by remember { mutableStateOf(editingKey?.modelName ?: "gpt-4o-mini") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var providerDropdownExpanded by remember { mutableStateOf(false) }

        // Fetch models states for OpenAI-compatible keys
        var isFetchingKeyModels by remember { mutableStateOf(false) }
        var fetchedKeyModels by remember { mutableStateOf<List<UserAiModel>?>(null) }
        var fetchKeyError by remember { mutableStateOf<String?>(null) }
        var keyModelSearchQuery by remember { mutableStateOf("") }
        var autoRegisterInAiBrain by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            containerColor = activeCardBg,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .imePadding(),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = activeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (editingKey == null) "Add API Key" else "Edit API Key",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 2.dp)
                    ) {
                        Text("Service Provider", color = TextSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { providerDropdownExpanded = !providerDropdownExpanded },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1B1F2A),
                                border = BorderStroke(1.dp, if (providerDropdownExpanded) activeAccent else CardBorderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dropdown_service_provider")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(selectedRole.iconEmoji, fontSize = 18.sp)
                                        Column {
                                            Text(
                                                text = selectedRole.displayName,
                                                color = TextMain,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = selectedRole.subtitle,
                                                color = TextSub,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (providerDropdownExpanded) Icons.Filled.ExpandLess else Icons.Filled.ArrowDropDown,
                                        contentDescription = "Expand Provider Menu",
                                        tint = activeAccent
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = { providerDropdownExpanded = false },
                                modifier = Modifier
                                    .background(Color(0xFF1A1D27))
                                    .border(1.dp, CardBorderColor, RoundedCornerShape(8.dp))
                                    .widthIn(min = 280.dp, max = 340.dp)
                            ) {
                                ApiRole.entries.forEach { role ->
                                    val isSelected = selectedRole == role
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(role.iconEmoji, fontSize = 16.sp)
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = role.displayName,
                                                        color = if (isSelected) activeAccent else TextMain,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                    Text(
                                                        text = role.subtitle,
                                                        color = TextSub,
                                                        fontSize = 9.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Selected",
                                                        tint = activeAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedRole = role
                                            if (labelInput.isBlank() || labelInput.startsWith("My ") || labelInput.endsWith(" Key")) {
                                                labelInput = "${role.displayName} Key"
                                            }
                                            fetchKeyError = null
                                            fetchedKeyModels = null
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                    if (selectedRole.portalUrl.isNotBlank()) {
                        Surface(
                            onClick = { openWebUrl(context, selectedRole.portalUrl) },
                            color = activeAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Need a ${selectedRole.displayName} key?",
                                        color = TextMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Get free API access at ${selectedRole.portalTitle}",
                                        color = activeAccent,
                                        fontSize = 10.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Web Link",
                                    tint = activeAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Label / Identifier") },
                        placeholder = { Text("e.g. Primary Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = CardBorderColor,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = {
                            keyInput = it
                            fetchKeyError = null
                        },
                        label = { Text("API Secret Key") },
                        placeholder = { Text(selectedRole.headerName) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null,
                                    tint = TextSub
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = CardBorderColor,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedRole == ApiRole.OPENAI_COMPATIBLE) {
                        // Quick endpoint presets
                        Text("Provider Preset:", color = TextSub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presets = listOf(
                                Triple("OpenRouter", "https://openrouter.ai/api/v1/", "deepseek/deepseek-r1"),
                                Triple("OpenAI", "https://api.openai.com/v1/", "gpt-4o-mini"),
                                Triple("DeepSeek", "https://api.deepseek.com/v1/", "deepseek-chat"),
                                Triple("Groq", "https://api.groq.com/openai/v1/", "llama-3.3-70b-versatile"),
                                Triple("Ollama", "http://10.0.2.2:11434/v1/", "llama3.2")
                            )
                            items(presets) { (presetName, url, defaultModel) ->
                                val isSelected = endpointInput == url
                                Surface(
                                    onClick = {
                                        endpointInput = url
                                        if (modelNameInput.isBlank() || modelNameInput == "gpt-4o-mini") {
                                            modelNameInput = defaultModel
                                        }
                                        fetchKeyError = null
                                        fetchedKeyModels = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) activeAccent.copy(alpha = 0.2f) else Color(0xFF1E222B),
                                    border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF2E3446))
                                ) {
                                    Text(
                                        text = presetName,
                                        color = if (isSelected) activeAccent else TextMain,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = {
                                endpointInput = it
                                fetchKeyError = null
                            },
                            label = { Text("Base URL Endpoint") },
                            placeholder = { Text("https://api.openai.com/v1/") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeAccent,
                                unfocusedBorderColor = CardBorderColor,
                                focusedTextColor = TextMain,
                                unfocusedTextColor = TextMain
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("input_api_key_endpoint")
                        )

                        // FETCH MODELS BUTTON FOR OPENAI COMPATIBLE
                        Button(
                            onClick = {
                                isFetchingKeyModels = true
                                fetchKeyError = null
                                viewModel.fetchAvailableAiModels(endpointInput.trim(), keyInput.trim()) { result ->
                                    isFetchingKeyModels = false
                                    if (result.isSuccess) {
                                        val list = result.getOrNull().orEmpty()
                                        if (list.isEmpty()) {
                                            fetchKeyError = "Endpoint returned 0 models. Verify URL and credentials."
                                        } else {
                                            fetchedKeyModels = list
                                            if (list.isNotEmpty() && (modelNameInput.isBlank() || modelNameInput == "gpt-4o-mini")) {
                                                modelNameInput = list.first().id
                                            }
                                        }
                                    } else {
                                        fetchKeyError = result.exceptionOrNull()?.message ?: "Failed to connect to endpoint"
                                    }
                                }
                            },
                            enabled = !isFetchingKeyModels,
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_fetch_models_in_add_key")
                        ) {
                            if (isFetchingKeyModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Querying Endpoint Models...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (fetchedKeyModels != null) "Re-fetch Available Models (${fetchedKeyModels!!.size} found)" else "Fetch Models from Endpoint",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (fetchKeyError != null) {
                            Surface(
                                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ $fetchKeyError",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Display fetched models selector if available
                        if (fetchedKeyModels != null) {
                            val models = fetchedKeyModels!!
                            val filtered = models.filter {
                                keyModelSearchQuery.isBlank() ||
                                it.name.contains(keyModelSearchQuery, ignoreCase = true) ||
                                it.id.contains(keyModelSearchQuery, ignoreCase = true)
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select Model (${filtered.size}):",
                                        color = activeAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap model to select",
                                        color = TextSub,
                                        fontSize = 10.sp
                                    )
                                }

                                if (models.size > 5) {
                                    OutlinedTextField(
                                        value = keyModelSearchQuery,
                                        onValueChange = { keyModelSearchQuery = it },
                                        placeholder = { Text("Filter fetched models...", fontSize = 11.sp) },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSub, modifier = Modifier.size(14.dp)) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = activeAccent,
                                            unfocusedBorderColor = CardBorderColor,
                                            focusedTextColor = TextMain,
                                            unfocusedTextColor = TextMain
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    )
                                }

                                Surface(
                                    color = Color(0xFF161920),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF262A36)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                ) {
                                    if (filtered.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("No models matching filter", color = TextSub, fontSize = 11.sp)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(filtered, key = { it.id }) { model ->
                                                val isSelected = modelNameInput == model.id
                                                Surface(
                                                    onClick = {
                                                        modelNameInput = model.id
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) activeAccent.copy(alpha = 0.2f) else Color(0xFF1E222B),
                                                    border = BorderStroke(1.dp, if (isSelected) activeAccent else Color(0xFF2E3446)),
                                                    modifier = Modifier.fillMaxWidth().testTag("fetched_model_item_${model.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = model.name,
                                                                color = if (isSelected) activeAccent else TextMain,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = model.id,
                                                                color = TextSub,
                                                                fontSize = 9.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Check,
                                                                contentDescription = "Selected",
                                                                tint = activeAccent,
                                                                modifier = Modifier.size(16.dp)
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

                        OutlinedTextField(
                            value = modelNameInput,
                            onValueChange = { modelNameInput = it },
                            label = { Text("Model Identifier") },
                            placeholder = { Text("gpt-4o-mini / deepseek-r1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = activeAccent,
                                unfocusedBorderColor = CardBorderColor,
                                focusedTextColor = TextMain,
                                unfocusedTextColor = TextMain
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("input_api_key_model_name")
                        )

                        // Auto register checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoRegisterInAiBrain = !autoRegisterInAiBrain }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = autoRegisterInAiBrain,
                                onCheckedChange = { autoRegisterInAiBrain = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = activeAccent,
                                    uncheckedColor = TextSub,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Register this model into AI Brain Engine",
                                color = TextMain,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            val newKey = ManagedApiKey(
                                id = editingKey?.id ?: java.util.UUID.randomUUID().toString(),
                                role = selectedRole.code,
                                key = keyInput.trim(),
                                label = labelInput.ifBlank { "${selectedRole.displayName} Key" },
                                endpointUrl = endpointInput.trim(),
                                modelName = modelNameInput.trim(),
                                status = KeyStatus.ACTIVE.name
                            )
                            viewModel.keyManager.addOrUpdateKey(newKey)

                            // Also register in AI Brain models list if requested
                            if (selectedRole == ApiRole.OPENAI_COMPATIBLE && autoRegisterInAiBrain && modelNameInput.isNotBlank()) {
                                val matchedModel = fetchedKeyModels?.find { it.id == modelNameInput.trim() }
                                val modelToAdd = matchedModel?.copy(
                                    endpointUrl = endpointInput.trim(),
                                    apiKey = keyInput.trim()
                                ) ?: UserAiModel(
                                    id = modelNameInput.trim(),
                                    name = modelNameInput.trim().split("/").last().replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                    provider = "OpenAI Compatible",
                                    endpointUrl = endpointInput.trim(),
                                    apiKey = keyInput.trim(),
                                    badge = "Vault API",
                                    description = "Added via API Vault (${labelInput.ifBlank { "OpenAI Compatible" }})"
                                )
                                viewModel.addUserModel(modelToAdd)
                            }

                            showAddKeyDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Saved key for ${selectedRole.displayName}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                ) {
                    Text("Save to Vault", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) {
                    Text("Cancel", color = TextSub)
                }
            }
        )
    }

    // 2. CUSTOM TACTICAL PROMPT DIALOG
    if (showCustomPromptDialog) {
        var promptText by remember { mutableStateOf(customSettings.customTacticalPrompt) }

        AlertDialog(
            onDismissRequest = { showCustomPromptDialog = false },
            containerColor = activeCardBg,
            title = {
                Text("Custom Tactical AI Directives", color = TextMain, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Provide specialized guidelines for the AI predictor (e.g., 'Focus heavily on defensive suspensions and expected corner averages').",
                        color = TextSub,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = { Text("Enter tactical instructions...") },
                        maxLines = 6,
                        minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = CardBorderColor,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCustomTacticalPrompt(promptText.trim())
                        showCustomPromptDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Updated tactical instructions")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                ) {
                    Text("Apply Prompt", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPromptDialog = false }) {
                    Text("Cancel", color = TextSub)
                }
            }
        )
    }

    // 3. EXPORT / IMPORT SETTINGS DIALOG
    if (showExportImportDialog) {
        var jsonText by remember { mutableStateOf(viewModel.exportSettingsJson()) }
        var importError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showExportImportDialog = false },
            containerColor = activeCardBg,
            title = {
                Text("Backup & Restore Config", color = TextMain, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Export your configuration or paste an existing backup JSON string below.",
                        color = TextSub,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = {
                            jsonText = it
                            importError = null
                        },
                        maxLines = 8,
                        minLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = CardBorderColor,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (importError != null) {
                        Text(text = importError!!, color = Color(0xFFFF5252), fontSize = 11.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard("App Config JSON", jsonText) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, activeAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, tint = activeAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy JSON", color = activeAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = viewModel.importSettingsJson(jsonText)
                        if (success) {
                            showExportImportDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Configuration imported successfully!")
                            }
                        } else {
                            importError = "Invalid JSON format. Please verify syntax."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                ) {
                    Text("Import & Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportImportDialog = false }) {
                    Text("Close", color = TextSub)
                }
            }
        )
    }

    // 4. EXPORT SLIPS DIALOG
    if (showExportSlipsDialog) {
        val slipReport = remember { viewModel.exportSlipsAsFormattedText() }

        AlertDialog(
            onDismissRequest = { showExportSlipsDialog = false },
            containerColor = activeCardBg,
            title = {
                Text("Exported Bet Slips", color = TextMain, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Formatted summary of all saved bet predictions:",
                        color = TextSub,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = Color(0xFF161920),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(
                                    text = slipReport,
                                    color = TextMain,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard("Saved Slips Report", slipReport)
                        showExportSlipsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                ) {
                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to Clipboard", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportSlipsDialog = false }) {
                    Text("Close", color = TextSub)
                }
            }
        )
    }

    // 5. RESET CONFIRM DIALOG
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = activeCardBg,
            title = {
                Text("Reset All Settings?", color = TextMain, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will restore default appearance, AI reasoning depth, odds format, stake presets, and active bet markets to factory defaults.",
                    color = TextSub,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettingsToDefault()
                        showResetConfirmDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("All settings restored to defaults")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Reset Defaults", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = TextSub)
                }
            }
        )
    }


}

// ==================== SUBCOMPONENTS ====================

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    activeAccent: Color,
    cardBg: Color,
    initiallyExpanded: Boolean = false,
    summaryBadge: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = activeAccent.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = subtitle,
                        color = TextSub,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        maxLines = if (isExpanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (summaryBadge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    summaryBadge()
                }
                if (action != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    action()
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = activeAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = activeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CardBorderColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}



@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeAccent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(text = subtitle, color = TextSub, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = activeAccent,
                uncheckedThumbColor = TextSub,
                uncheckedTrackColor = Color(0xFF262A36)
            )
        )
    }
}

@Composable
private fun RoleKeysSection(
    role: ApiRole,
    keys: List<ManagedApiKey>,
    activeAccent: Color,
    isTestingKeyId: String?,
    onTestKey: (ManagedApiKey) -> Unit,
    onEditKey: (ManagedApiKey) -> Unit,
    onDeleteKey: (ManagedApiKey) -> Unit,
    onResetStats: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(keys.isNotEmpty()) }

    Surface(
        color = Color(0xFF1E222B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = role.iconEmoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = role.displayName,
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (role.portalUrl.isNotBlank()) {
                        Surface(
                            onClick = { openWebUrl(context, role.portalUrl) },
                            color = activeAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Get Key",
                                    color = activeAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Link",
                                    tint = activeAccent,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        color = if (keys.isNotEmpty()) activeAccent.copy(alpha = 0.15f) else Color(0xFF262A36),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${keys.size} stored",
                            color = if (keys.isNotEmpty()) activeAccent else TextSub,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle Role Keys",
                        tint = activeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(text = role.subtitle, color = TextSub, fontSize = 10.sp)

            if (isExpanded) {
                if (keys.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    keys.forEach { keyItem ->
                        val isTesting = isTestingKeyId == keyItem.id
                        Surface(
                            color = Color(0xFF161920),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF262A36)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = keyItem.label,
                                        color = TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = keyItem.maskedKey,
                                        color = TextSub,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (keyItem.lastTestMessage != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = keyItem.lastTestMessage,
                                            color = if (keyItem.lastTestStatus == "ACTIVE") Color(0xFF00E676) else Color(0xFFFF5252),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = activeAccent, strokeWidth = 2.dp)
                                    } else {
                                        IconButton(
                                            onClick = { onTestKey(keyItem) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = "Test",
                                                tint = activeAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onEditKey(keyItem) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit",
                                            tint = TextSub,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteKey(keyItem) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No keys configured yet for this provider. Tap '+ Add New API Key' above to add one.",
                        color = TextSub.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}

private fun openWebUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
