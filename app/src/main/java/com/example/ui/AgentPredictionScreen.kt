package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.BatchItemStatus
import com.example.viewmodel.PredictorViewModel

private val DarkCanvas = Color(0xFF0A0C10)
private val CardBg = Color(0xFF12151C)
private val TerminalBg = Color(0xFF0D0F15)
private val BorderColor = Color(0xFF222733)
private val TextWhite = Color(0xFFECEFF1)
private val TextMuted = Color(0xFF78909C)
private val CyberEmerald = Color(0xFF00E676)
private val CyberCyan = Color(0xFF00E5FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPredictionScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onShowBets: () -> Unit = {}
) {
    val batchItems by viewModel.batchMatchItems.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAgentRunning.collectAsStateWithLifecycle()
    val logs by viewModel.agentLogs.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentActivePredictingIndex.collectAsStateWithLifecycle()
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val useLocalEngineOnly by viewModel.useLocalEngineOnly.collectAsStateWithLifecycle()
    val activeAccent = customSettings.accentColorMode.color
    val apiFallbackPrompt by viewModel.apiFallbackPrompt.collectAsStateWithLifecycle()

    val hasAiKey = viewModel.hasConfiguredAiKey()
    val activeKeyMasked = viewModel.getActiveAiKeyMasked()

    LaunchedEffect(Unit) {
        viewModel.prepareBatchForPrediction()
    }

    val selectedItems = batchItems.filter { it.isSelected }
    val totalSelected = selectedItems.size
    val totalFinished = selectedItems.count { it.status == BatchItemStatus.FINISHED }
    val isAllFinished = totalSelected > 0 && totalFinished == totalSelected && !isRunning
    val progress = if (totalSelected > 0) totalFinished.toFloat() / totalSelected.toFloat() else 0f

    val currentMatch = if (currentIndex in batchItems.indices) {
        batchItems[currentIndex]
    } else if (isAllFinished && selectedItems.isNotEmpty()) {
        selectedItems.last()
    } else {
        selectedItems.firstOrNull { it.status == BatchItemStatus.PENDING || it.status == BatchItemStatus.PREDICTING }
            ?: selectedItems.firstOrNull()
    }

    // High tech scanning animation for terminal and status
    val infiniteTransition = rememberInfiniteTransition(label = "tech_radar")
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "AI Prediction Core",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Quantum state chip
                                Surface(
                                    color = if (isRunning) activeAccent.copy(alpha = 0.15f) else if (isAllFinished) CyberEmerald.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isRunning) activeAccent else if (isAllFinished) CyberEmerald else BorderColor)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                    color = if (isRunning) activeAccent.copy(alpha = radarAlpha) else if (isAllFinished) CyberEmerald else TextMuted,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isRunning) "ORACLE ACTIVE" else if (isAllFinished) "COMPLETED" else "STANDBY",
                                            color = if (isRunning) activeAccent else if (isAllFinished) CyberEmerald else TextMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Deep Neural Reasoning Pipeline",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = !isRunning,
                        modifier = Modifier.testTag("btn_agent_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isRunning) TextMuted else activeAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCanvas,
                    titleContentColor = TextWhite
                )
            )
        },
        bottomBar = {
            Surface(
                color = CardBg,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!isRunning && !isAllFinished) {
                        Button(
                            onClick = { viewModel.startAgentPredictionLoop() },
                            enabled = totalSelected > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_run_ai_agent")
                        ) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Start Autonomous Predictions ($totalSelected)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // "Show Bets" Button - Grayed out until process finish
                        Button(
                            onClick = onShowBets,
                            enabled = isAllFinished,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberEmerald,
                                disabledContainerColor = Color(0xFF1E222D),
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_show_bets")
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = activeAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Processing ($totalFinished/$totalSelected Completed)...",
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.SportsSoccer,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "SHOW PREDICTED BETS",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = DarkCanvas,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 0. Active Prediction Engine Status
            Surface(
                color = if (useLocalEngineOnly) Color(0xFF132019) else if (hasAiKey) Color(0xFF141D29) else Color(0xFF281C16),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (useLocalEngineOnly) CyberEmerald.copy(alpha = 0.5f) else if (hasAiKey) Color(0xFF2E3F59) else Color(0xFFFF9800).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (useLocalEngineOnly) Icons.Filled.Bolt else Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = if (useLocalEngineOnly) CyberEmerald else if (hasAiKey) activeAccent else Color(0xFFFF9800),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (useLocalEngineOnly) "Local Engine: Poisson & Quant xG (Offline)" else "AI Model: ${customSettings.activeAiModelId}",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (useLocalEngineOnly) "No API key required • 100% On-Device" else if (hasAiKey) "API Key Active ($activeKeyMasked)" else "⚠️ No API Key (Prompt on run)",
                                color = if (useLocalEngineOnly) CyberEmerald else if (hasAiKey) TextMuted else Color(0xFFFFB74D),
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (!isRunning) {
                        Surface(
                            onClick = { viewModel.setUseLocalEngineOnly(!useLocalEngineOnly) },
                            color = Color(0xFF1F2430),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text(
                                text = if (useLocalEngineOnly) "Switch to AI" else "Use Local",
                                color = if (useLocalEngineOnly) activeAccent else CyberEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 1. SLEEK COMPACT PROGRESS BAR: "Done 3 / 10"
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = if (isAllFinished) CyberEmerald else activeAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "BATCH PROGRESS",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Done $totalFinished / $totalSelected",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isAllFinished) CyberEmerald.copy(alpha = 0.15f) else Color(0xFF1E222B),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isAllFinished) CyberEmerald else BorderColor)
                            ) {
                                val remaining = (totalSelected - totalFinished).coerceAtLeast(0)
                                Text(
                                    text = if (isAllFinished) "100%" else "$remaining Left",
                                    color = if (isAllFinished) CyberEmerald else activeAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Futuristic HUD Gauge
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isAllFinished) CyberEmerald else activeAccent,
                        trackColor = Color(0xFF1E222D)
                    )
                }
            }

            // 2. ULTRA-COMPACT SLIM MATCH BANNER (Takes minimal height, maximum density)
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isRunning) activeAccent.copy(alpha = 0.7f) else if (isAllFinished) CyberEmerald.copy(alpha = 0.7f) else BorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compact_match_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mini Status Indicator Dot / Icon
                    Surface(
                        color = if (isRunning) activeAccent.copy(alpha = 0.2f) else if (isAllFinished) CyberEmerald.copy(alpha = 0.2f) else Color(0xFF1E222B),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (isRunning) activeAccent else if (isAllFinished) CyberEmerald else BorderColor),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = activeAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isAllFinished) Icons.Filled.CheckCircle else Icons.Filled.Psychology,
                                    contentDescription = null,
                                    tint = if (isAllFinished) CyberEmerald else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Match Title & 1-line Agent Action Subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (currentMatch != null) "${currentMatch.homeTeam} vs ${currentMatch.awayTeam}" else "No match in queue",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentMatch != null) {
                                Text(
                                    text = currentMatch.leagueName,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isRunning) "▶ " else if (isAllFinished) "✔ " else "⏳ ",
                                color = if (isRunning) activeAccent else if (isAllFinished) CyberEmerald else TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = if (isRunning) {
                                    currentMatch?.currentAgentAction ?: "Predicting..."
                                } else if (isAllFinished) {
                                    "Ready: ${currentMatch?.prediction?.recommendedBet ?: "Predictions Finalized"}"
                                } else {
                                    "Queued in pipeline (${currentMatch?.startTime ?: "Upcoming"})"
                                },
                                color = if (isAllFinished) CyberEmerald else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 3. HIGH-TECH CYBER-HUD REASONING STREAM (Smooth Terminal & Thought Engine)
            Surface(
                color = TerminalBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isRunning) activeAccent.copy(alpha = 0.4f) else BorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Terminal Top Bar HUD Header
                    Surface(
                        color = Color(0xFF141822),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = null,
                                    tint = if (isRunning) activeAccent else CyberCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "ORACLE REASONING CORE // LIVE STREAM",
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (isRunning) activeAccent else CyberEmerald,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRunning) "STREAMING" else "IDLE",
                                    color = if (isRunning) activeAccent else CyberEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1C2230), thickness = 1.dp)

                    // Logs Content Area with Smooth Animation
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Sensors,
                                    contentDescription = null,
                                    tint = TextMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Neural reasoning stream standing by...\nPress Start to trigger autonomous prediction cycle.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            reverseLayout = true // Newest logs visible at top
                        ) {
                            items(logs.reversed()) { log ->
                                val (tagBadge, badgeColor, textColor) = when (log.type) {
                                    "SEARCH" -> Triple("DATA", CyberCyan, Color(0xFF80DEEA))
                                    "AI" -> Triple("NEURAL", activeAccent, Color(0xFFFFCC80))
                                    "SUCCESS" -> Triple("OUTPUT", CyberEmerald, Color(0xFFA5D6A7))
                                    "WARN" -> Triple("ALERT", Color(0xFFFF5252), Color(0xFFFF8A80))
                                    else -> Triple("CORE", TextMuted, TextWhite)
                                }

                                Surface(
                                    color = Color(0xFF10131B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF1A1F2B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // High-Tech Cyber Tag Badge
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                                        ) {
                                            Text(
                                                text = tagBadge,
                                                color = badgeColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = log.message,
                                                    color = textColor,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = log.timestamp,
                                                    color = TextMuted.copy(alpha = 0.6f),
                                                    fontSize = 9.sp,
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

        apiFallbackPrompt?.let { prompt ->
            ApiFallbackPromptDialog(
                prompt = prompt,
                activeAccent = activeAccent,
                onSelectDecision = { decision ->
                    viewModel.submitFallbackDecision(decision)
                }
            )
        }
    }
}

@Composable
fun ApiFallbackPromptDialog(
    prompt: com.example.models.ApiFallbackPrompt,
    activeAccent: Color,
    onSelectDecision: (com.example.models.FallbackDecision) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Force explicit user choice */ },
        containerColor = Color(0xFF131722),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFFF9100).copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9100),
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "External API Error",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Prediction Fallback Required",
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Match Header Context
                Surface(
                    color = Color(0xFF1C2230),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF263246)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MATCH ${prompt.matchIndex} OF ${prompt.totalMatches}",
                                color = activeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = prompt.leagueName,
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${prompt.homeTeam} vs ${prompt.awayTeam}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Error Details Card
                Surface(
                    color = Color(0xFF2B161B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF5C232B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MODEL: ${prompt.modelName.uppercase()}",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt.errorMessage,
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Explanation
                Text(
                    text = "Would you like to switch to the On-Device Local Mathematical Engine (Statistical Poisson & Quant xG) to predict this fixture, retry the external API, or cancel?",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary: Local Engine for this match
                Button(
                    onClick = { onSelectDecision(com.example.models.FallbackDecision.USE_LOCAL_ONCE) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Use Local Engine (This Match)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Option: Local Engine for all remaining
                Button(
                    onClick = { onSelectDecision(com.example.models.FallbackDecision.USE_LOCAL_ALL) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                    border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null, tint = activeAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Use Local Engine for All Remaining",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                // Bottom row: Retry and Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSelectDecision(com.example.models.FallbackDecision.RETRY_API) },
                        border = BorderStroke(1.dp, Color(0xFF37474F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = TextWhite, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry API", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onSelectDecision(com.example.models.FallbackDecision.CANCEL) },
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel Batch", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        dismissButton = null
    )
}
