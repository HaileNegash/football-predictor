package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.PredictedBetItem
import com.example.models.SavedPredictionSlip
import com.example.viewmodel.PredictorViewModel

private val DarkCanvas = Color(0xFF0A0C10)
private val CardBg = Color(0xFF12151C)
private val InnerCardBg = Color(0xFF181D26)
private val BorderColor = Color(0xFF222733)
private val TextWhite = Color(0xFFECEFF1)
private val TextMuted = Color(0xFF8899A6)
private val CyberEmerald = Color(0xFF00E676)
private val CyberAmber = Color(0xFFFF6D00)
private val CyberGold = Color(0xFFFFD600)
private val CyberCyan = Color(0xFF00E5FF)
private val CyberRed = Color(0xFFFF5252)

data class ScoreEditTarget(
    val slipId: String,
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val currentHomeScore: Int?,
    val currentAwayScore: Int?,
    val currentStatus: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsHistoryScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onViewSlipDetails: () -> Unit
) {
    val slips by viewModel.savedSlipsHistory.collectAsStateWithLifecycle()
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingOutcomes.collectAsStateWithLifecycle()
    val activeAccent = customSettings.accentColorMode.color
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var slipToDelete by remember { mutableStateOf<String?>(null) }
    var scoreEditTarget by remember { mutableStateOf<ScoreEditTarget?>(null) }

    // Map to track expanded slips in the list
    val expandedSlips = remember { mutableStateMapOf<String, Boolean>() }
    // Map to track expanded rationales inside matches
    val expandedReasons = remember { mutableStateMapOf<String, Boolean>() }

    val filteredSlips = remember(slips, searchQuery) {
        if (searchQuery.isBlank()) {
            slips
        } else {
            val q = searchQuery.trim().lowercase()
            slips.filter { slip ->
                slip.slipId.lowercase().contains(q) ||
                slip.dateString.lowercase().contains(q) ||
                slip.overallStatus.lowercase().contains(q) ||
                slip.items.any {
                    it.homeTeam.lowercase().contains(q) ||
                    it.awayTeam.lowercase().contains(q) ||
                    it.leagueName.lowercase().contains(q) ||
                    it.recommendedBet.lowercase().contains(q) ||
                    (it.outcomeStatus.lowercase().contains(q))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = activeAccent.copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, activeAccent),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.FactCheck,
                                    contentDescription = null,
                                    tint = activeAccent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Bets & Results Checker",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF1E222D),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${slips.size} Slips",
                                        color = activeAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Verify predictions & track match results",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_history_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = activeAccent
                        )
                    }
                },
                actions = {
                    if (slips.isNotEmpty()) {
                        // Check All / Verify button in top bar
                        IconButton(
                            onClick = {
                                viewModel.verifyAllSavedSlips(simulateIfMissing = false)
                                Toast.makeText(context, "Checking outcomes against loaded match scores...", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isChecking,
                            modifier = Modifier.testTag("btn_verify_all_slips")
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = activeAccent, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Check All Slips",
                                    tint = activeAccent
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val text = viewModel.exportSlipsAsFormattedText()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Bet Slips", text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied slips & results report to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("btn_history_export_slips")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Slips Report",
                                tint = CyberCyan
                            )
                        }
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("btn_history_clear_all")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Clear All Slips",
                                tint = TextMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCanvas,
                    titleContentColor = TextWhite
                )
            )
        },
        containerColor = DarkCanvas,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {
            // 1. STATS OVERVIEW CARDS & GLOBAL VERIFIER (If history exists)
            if (slips.isNotEmpty()) {
                val totalMatches = slips.sumOf { it.totalMatches }
                val wonSlips = slips.count { it.overallStatus == "WON" }
                val lostSlips = slips.count { it.overallStatus == "LOST" }
                val pendingSlips = slips.count { it.overallStatus in listOf("PENDING", "IN_PROGRESS") }
                val totalChecked = wonSlips + lostSlips
                val winRate = if (totalChecked > 0) (wonSlips * 100) / totalChecked else 0

                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HistoryStatItem(
                                label = "TOTAL SLIPS",
                                value = "${slips.size}",
                                color = activeAccent,
                                modifier = Modifier.weight(1f)
                            )
                            HistoryStatItem(
                                label = "SLIPS WON",
                                value = if (totalChecked > 0) "$wonSlips ($winRate%)" else "$wonSlips",
                                color = CyberEmerald,
                                modifier = Modifier.weight(1.2f)
                            )
                            HistoryStatItem(
                                label = "IN PROGRESS",
                                value = "$pendingSlips",
                                color = CyberAmber,
                                modifier = Modifier.weight(1f)
                            )
                            HistoryStatItem(
                                label = "MATCHES",
                                value = "$totalMatches",
                                color = CyberCyan,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Multi-action bar for verifying outcomes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.verifyAllSavedSlips(simulateIfMissing = false)
                                    Toast.makeText(context, "Checked all slips against match scores", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("btn_check_all_outcomes")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FactCheck,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Check Outcomes",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.verifyAllSavedSlips(simulateIfMissing = true)
                                    Toast.makeText(context, "Simulated full-time match scores & evaluated all slips", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = InnerCardBg),
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("btn_simulate_all_outcomes")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Simulate Outcomes",
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. SEARCH / FILTER BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search team, slip ID, WON, or LOST...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = activeAccent,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("input_history_search")
                )
            }

            // 3. SLIPS LIST
            if (filteredSlips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = Color(0xFF141822),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = TextMuted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching slips found" else "No Bet Slips Generated Yet",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching by team, outcome status (WON/LOST) or slip ID." else "Select matches on the home screen and run an autonomous prediction cycle. You can check match results here once predictions are generated.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSlips, key = { it.slipId }) { slip ->
                        val isSlipExpanded = expandedSlips[slip.slipId] ?: false
                        val arrowRot by animateFloatAsState(
                            targetValue = if (isSlipExpanded) 180f else 0f,
                            label = "slip_arrow"
                        )

                        Surface(
                            color = CardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                when (slip.overallStatus) {
                                    "WON" -> CyberEmerald.copy(alpha = 0.6f)
                                    "LOST" -> CyberRed.copy(alpha = 0.5f)
                                    "IN_PROGRESS" -> CyberAmber.copy(alpha = 0.5f)
                                    else -> if (isSlipExpanded) activeAccent.copy(alpha = 0.6f) else BorderColor
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_slip_${slip.slipId}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Top Header of Slip with Slip ID and Overall Status Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = activeAccent.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = slip.slipId,
                                                color = activeAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = slip.dateString,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    // Status Badge on Top Right
                                    SlipOutcomeBadge(status = slip.overallStatus)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Slip Key Highlights Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(InnerCardBg, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${slip.totalMatches} Matches",
                                            color = TextWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = CyberEmerald.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${slip.averageConfidence}% Conf",
                                                color = CyberEmerald,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = CyberGold.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "Combo @${slip.totalCombinedOdds}",
                                            color = CyberGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Verification Stats Bar (Won / Lost / Pending breakdown)
                                if (slip.wonItemsCount > 0 || slip.lostItemsCount > 0 || slip.voidItemsCount > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0E1016), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (slip.wonItemsCount > 0) {
                                                Text(
                                                    text = "✓ ${slip.wonItemsCount} Won",
                                                    color = CyberEmerald,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (slip.lostItemsCount > 0) {
                                                Text(
                                                    text = "✗ ${slip.lostItemsCount} Lost",
                                                    color = CyberRed,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (slip.pendingItemsCount > 0) {
                                                Text(
                                                    text = "⏳ ${slip.pendingItemsCount} Pending",
                                                    color = CyberAmber,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Checked",
                                            color = TextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Slip Quick Action Buttons: Verify, Simulate, Open, Copy, Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Verify Single Slip Button
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.verifySlipOutcomes(slip.slipId, simulateIfMissing = false)
                                                Toast.makeText(context, "Checking scores for ${slip.slipId}...", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f)),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FactCheck,
                                                contentDescription = null,
                                                tint = activeAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                "Check",
                                                color = activeAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Simulate Outcomes Button
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.verifySlipOutcomes(slip.slipId, simulateIfMissing = true)
                                                Toast.makeText(context, "Simulated match outcomes for ${slip.slipId}", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = CyberCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                "Simulate",
                                                color = CyberCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Reset Verification Status
                                        if (slip.overallStatus != "PENDING") {
                                            IconButton(
                                                onClick = {
                                                    viewModel.resetSlipOutcomes(slip.slipId)
                                                    Toast.makeText(context, "Reset status for ${slip.slipId}", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.RestartAlt,
                                                    contentDescription = "Reset",
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Copy Slip Action
                                        IconButton(
                                            onClick = { copySlipToClipboard(context, slip) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        // Delete Single Slip Action
                                        IconButton(
                                            onClick = { slipToDelete = slip.slipId },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = TextMuted.copy(alpha = 0.7f),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Bottom Row: Open Dedicated Slip View & Accordion Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.selectSlip(slip)
                                            onViewSlipDetails()
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = activeAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Open Slip View",
                                            color = activeAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .clickable { expandedSlips[slip.slipId] = !isSlipExpanded }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSlipExpanded) "Hide Matches" else "Show Matches",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .rotate(arrowRot)
                                        )
                                    }
                                }

                                // EXPANDED ACCORDION: Matches inside the slip with outcome checks
                                AnimatedVisibility(
                                    visible = isSlipExpanded,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = BorderColor, thickness = 1.dp)

                                        slip.items.forEachIndexed { idx, item ->
                                            val reasonKey = "${slip.slipId}_${item.matchId}"
                                            val isReasonExpanded = expandedReasons[reasonKey] ?: false

                                            Surface(
                                                color = InnerCardBg,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    when (item.outcomeStatus) {
                                                        "WON" -> CyberEmerald.copy(alpha = 0.5f)
                                                        "LOST" -> CyberRed.copy(alpha = 0.5f)
                                                        else -> Color(0xFF222836)
                                                    }
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    // Match Header & Outcome Pill
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}. ${item.homeTeam} vs ${item.awayTeam}",
                                                            color = TextWhite,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "@${item.simulatedOdds}",
                                                                color = CyberGold,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            ItemOutcomeBadge(status = item.outcomeStatus)
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    // Pick & Odds info
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "🎯 Pick: ",
                                                                color = TextMuted,
                                                                fontSize = 11.sp
                                                            )
                                                            Text(
                                                                text = "[${item.betTypeCategory}] ${item.recommendedBet}",
                                                                color = activeAccent,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Text(
                                                            text = "${item.confidence}% Conf",
                                                            color = CyberEmerald,
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }

                                                    // Score / Verification explanation row
                                                    if (item.homeScore != null && item.awayScore != null) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Surface(
                                                            color = Color(0xFF0E1016),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.SportsSoccer,
                                                                        contentDescription = null,
                                                                        tint = CyberCyan,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text(
                                                                        text = "Score: ${item.homeScore} - ${item.awayScore} (${item.matchStatus ?: "FT"})",
                                                                        color = TextWhite,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )
                                                                }

                                                                // Edit score button
                                                                IconButton(
                                                                    onClick = {
                                                                        scoreEditTarget = ScoreEditTarget(
                                                                            slipId = slip.slipId,
                                                                            matchId = item.matchId,
                                                                            homeTeam = item.homeTeam,
                                                                            awayTeam = item.awayTeam,
                                                                            currentHomeScore = item.homeScore,
                                                                            currentAwayScore = item.awayScore,
                                                                            currentStatus = item.matchStatus ?: "FT"
                                                                        )
                                                                    },
                                                                    modifier = Modifier.size(20.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.Edit,
                                                                        contentDescription = "Edit score",
                                                                        tint = activeAccent,
                                                                        modifier = Modifier.size(12.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        // Button to enter custom score for testing
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    scoreEditTarget = ScoreEditTarget(
                                                                        slipId = slip.slipId,
                                                                        matchId = item.matchId,
                                                                        homeTeam = item.homeTeam,
                                                                        awayTeam = item.awayTeam,
                                                                        currentHomeScore = 0,
                                                                        currentAwayScore = 0,
                                                                        currentStatus = "FT"
                                                                    )
                                                                },
                                                                contentPadding = PaddingValues(0.dp)
                                                            ) {
                                                                Text(
                                                                    "+ Test Final Score",
                                                                    color = CyberCyan,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (!item.outcomeExplanation.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = item.outcomeExplanation,
                                                            color = if (item.outcomeStatus == "WON") CyberEmerald else if (item.outcomeStatus == "LOST") CyberRed else TextMuted,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }

                                                    // Toggle AI reasoning
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { expandedReasons[reasonKey] = !isReasonExpanded }
                                                            .padding(top = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (isReasonExpanded) "Hide tactical analysis" else "Show tactical analysis",
                                                            color = TextMuted,
                                                            fontSize = 10.sp
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Filled.ExpandMore,
                                                            contentDescription = null,
                                                            tint = TextMuted,
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .rotate(if (isReasonExpanded) 180f else 0f)
                                                        )
                                                    }

                                                    // Collapsed AI reasoning inside history item
                                                    AnimatedVisibility(
                                                        visible = isReasonExpanded,
                                                        enter = fadeIn() + expandVertically(),
                                                        exit = fadeOut() + shrinkVertically()
                                                    ) {
                                                        Surface(
                                                            color = Color(0xFF0E1016),
                                                            shape = RoundedCornerShape(6.dp),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 6.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(8.dp),
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Psychology,
                                                                    contentDescription = null,
                                                                    tint = CyberCyan,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = item.rationale,
                                                                    color = TextMuted,
                                                                    fontSize = 10.sp,
                                                                    lineHeight = 14.sp
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
                    }
                }
            }
        }

        // CUSTOM SCORE EDIT / TEST DIALOG
        scoreEditTarget?.let { target ->
            var homeScoreText by remember { mutableStateOf((target.currentHomeScore ?: 0).toString()) }
            var awayScoreText by remember { mutableStateOf((target.currentAwayScore ?: 0).toString()) }
            var statusText by remember { mutableStateOf(target.currentStatus ?: "FT") }

            AlertDialog(
                onDismissRequest = { scoreEditTarget = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.FactCheck, contentDescription = null, tint = activeAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify Match Score", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${target.homeTeam} vs ${target.awayTeam}",
                            color = activeAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Enter the final or live match score to check if the AI prediction was correct:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(target.homeTeam, color = TextWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = homeScoreText,
                                    onValueChange = { homeScoreText = it.filter { ch -> ch.isDigit() }.take(2) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF141822),
                                        unfocusedContainerColor = Color(0xFF141822),
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Text(":", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(target.awayTeam, color = TextWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = awayScoreText,
                                    onValueChange = { awayScoreText = it.filter { ch -> ch.isDigit() }.take(2) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF141822),
                                        unfocusedContainerColor = Color(0xFF141822),
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("FT", "HT", "90'", "AET").forEach { st ->
                                Surface(
                                    color = if (statusText == st) activeAccent else Color(0xFF181D26),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable { statusText = st }
                                ) {
                                    Text(
                                        text = st,
                                        color = if (statusText == st) Color.Black else TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val hScore = homeScoreText.toIntOrNull() ?: 0
                            val aScore = awayScoreText.toIntOrNull() ?: 0
                            viewModel.manuallySetMatchScore(target.slipId, target.matchId, hScore, aScore, statusText)
                            scoreEditTarget = null
                            Toast.makeText(context, "Score saved & prediction outcome evaluated!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeAccent)
                    ) {
                        Text("Apply & Verify", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { scoreEditTarget = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = CardBg,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // CONFIRM DELETE SINGLE SLIP DIALOG
        if (slipToDelete != null) {
            AlertDialog(
                onDismissRequest = { slipToDelete = null },
                title = { Text("Delete Prediction Slip?", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove slip $slipToDelete from your history?", color = TextMuted) },
                confirmButton = {
                    Button(
                        onClick = {
                            slipToDelete?.let { viewModel.deleteSlip(it) }
                            slipToDelete = null
                            Toast.makeText(context, "Slip deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { slipToDelete = null }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = CardBg,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // CONFIRM CLEAR ALL DIALOG
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("Clear All History?", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete all saved bet slips and verified results from your history. This action cannot be undone.", color = TextMuted) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllSlips()
                            showClearAllDialog = false
                            Toast.makeText(context, "All history cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Clear All", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = CardBg,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun SlipOutcomeBadge(status: String) {
    val (bgColor, textColor, text, icon) = when (status) {
        "WON" -> Quadruple(CyberEmerald.copy(alpha = 0.18f), CyberEmerald, "ALL WON", Icons.Filled.CheckCircle)
        "LOST" -> Quadruple(CyberRed.copy(alpha = 0.18f), CyberRed, "LOST", Icons.Filled.Cancel)
        "IN_PROGRESS" -> Quadruple(CyberAmber.copy(alpha = 0.18f), CyberAmber, "IN PROGRESS", Icons.Filled.HourglassEmpty)
        "PARTIAL" -> Quadruple(CyberCyan.copy(alpha = 0.18f), CyberCyan, "PARTIAL HIT", Icons.Filled.CheckCircle)
        else -> Quadruple(Color(0xFF262C38), TextMuted, "PENDING", Icons.Filled.HourglassEmpty)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ItemOutcomeBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "WON" -> Triple(CyberEmerald.copy(alpha = 0.2f), CyberEmerald, "✓ WON")
        "LOST" -> Triple(CyberRed.copy(alpha = 0.2f), CyberRed, "✗ LOST")
        "VOID" -> Triple(Color(0xFF2E3544), TextMuted, "— VOID")
        else -> Triple(Color(0xFF1E222D), CyberAmber, "⏳ PENDING")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun HistoryStatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF181D26),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun copySlipToClipboard(context: Context, slip: SavedPredictionSlip) {
    val sb = StringBuilder()
    sb.append("🎯 AI PREDICTED BET SLIP [${slip.slipId}]\n")
    sb.append("📅 ${slip.dateString}\n")
    sb.append("Status: ${slip.overallStatus}\n")
    sb.append("━━━━━━━━━━━━━━━━━━━\n")
    slip.items.forEachIndexed { i, item ->
        val tag = when (item.outcomeStatus) {
            "WON" -> "[✓ WON]"
            "LOST" -> "[✗ LOST]"
            "VOID" -> "[— VOID]"
            else -> "[⏳ PENDING]"
        }
        sb.append("${i + 1}. ${item.homeTeam} vs ${item.awayTeam} $tag\n")
        sb.append("   ▶ Pick: ${item.recommendedBet} (@${item.simulatedOdds})\n")
        if (item.homeScore != null && item.awayScore != null) {
            sb.append("   ▶ Score: ${item.homeScore}-${item.awayScore} (${item.matchStatus ?: "FT"})\n")
        }
        if (!item.outcomeExplanation.isNullOrBlank()) {
            sb.append("   ▶ ${item.outcomeExplanation}\n")
        }
        sb.append("   Confidence: ${item.confidence}%\n")
    }
    sb.append("━━━━━━━━━━━━━━━━━━━\n")
    sb.append("Combined Odds: @${slip.totalCombinedOdds} | Avg Conf: ${slip.averageConfidence}%\n")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Bet Slip", sb.toString())
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Slip copied to clipboard!", Toast.LENGTH_SHORT).show()
}
