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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import com.example.viewmodel.CloudSyncState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetsHistoryScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onViewSlipDetails: () -> Unit
) {
    val slips by viewModel.savedSlipsHistory.collectAsStateWithLifecycle()
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val accuracy by viewModel.accuracyStats.collectAsStateWithLifecycle()
    val isSettling by viewModel.isSettling.collectAsStateWithLifecycle()
    val activeAccent = customSettings.accentColorMode.color
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var slipToDelete by remember { mutableStateOf<String?>(null) }

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
                slip.items.any {
                    it.homeTeam.lowercase().contains(q) ||
                    it.awayTeam.lowercase().contains(q) ||
                    it.leagueName.lowercase().contains(q) ||
                    it.recommendedBet.lowercase().contains(q)
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
                                    imageVector = Icons.Filled.History,
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
                                    "Bets History",
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
                                text = "Saved Accumulators & AI Analyses",
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
                    IconButton(
                        onClick = { viewModel.settlePendingPredictions() },
                        enabled = !isSettling && slips.isNotEmpty(),
                        modifier = Modifier.testTag("btn_history_settle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = "Check results",
                            tint = if (isSettling) TextMuted else CyberCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.syncFromFirebaseCloud { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("btn_history_cloud_sync")
                    ) {
                        Icon(
                            imageVector = if (cloudSyncState == CloudSyncState.SYNCED) Icons.Filled.CloudDone else Icons.Filled.CloudSync,
                            contentDescription = "Sync Cloud Slips",
                            tint = if (cloudSyncState == CloudSyncState.SYNCED) CyberEmerald else activeAccent
                        )
                    }
                    if (slips.isNotEmpty()) {
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
            // 1. STATS OVERVIEW CARDS (If history exists)
            if (slips.isNotEmpty()) {
                val totalMatches = slips.sumOf { it.totalMatches }
                // Realised hit rate, not average claimed confidence. Averaging what
                // the model asserted about itself measures nothing; this measures
                // whether the picks actually landed.
                val hitRateLabel = accuracy.hitRate
                    ?.let { String.format(java.util.Locale.US, "%.0f%%", it * 100) }
                    ?: "—"

                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
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
                            label = "MATCHES PREDICTED",
                            value = "$totalMatches",
                            color = CyberCyan,
                            modifier = Modifier.weight(1.2f)
                        )
                        HistoryStatItem(
                            label = "HIT RATE (${accuracy.wonLegs + accuracy.lostLegs} SETTLED)",
                            value = hitRateLabel,
                            color = CyberEmerald,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    if (accuracy.pendingLegs > 0 || accuracy.roi != null || accuracy.ungradableLegs > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                accuracy.roi?.let {
                                    // Flat-staked return per unit. Below 1.0 means the
                                    // picks lost money even if the hit rate looks fine —
                                    // which is what happens when only short favourites
                                    // are selected.
                                    append(String.format(java.util.Locale.US, "Flat-stake return: %.2fx", it))
                                }
                                if (accuracy.pendingLegs > 0) {
                                    if (isNotEmpty()) append("  •  ")
                                    append("${accuracy.pendingLegs} legs awaiting results")
                                }
                                if (accuracy.ungradableLegs > 0) {
                                    // Surfaced rather than hidden: these legs finished but
                                    // couldn't be graded, so the hit rate above is computed
                                    // over a subset of the picks actually made.
                                    if (isNotEmpty()) append("  •  ")
                                    append("${accuracy.ungradableLegs} not gradable")
                                }
                            },
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Calibration: claimed vs realised, per confidence band. A gap
                    // here is the single most actionable signal about the model.
                    if (accuracy.buckets.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CALIBRATION — CLAIMED vs ACTUAL",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        accuracy.buckets.forEach { bucket ->
                            val actual = bucket.actualRate
                            val gap = bucket.overconfidenceGap
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bucket.label,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(56.dp)
                                )
                                Text(
                                    text = "claimed ${bucket.claimedAverage}%",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = actual?.let { String.format(java.util.Locale.US, "actual %.0f%%", it * 100) } ?: "actual —",
                                    color = when {
                                        gap == null -> TextMuted
                                        gap > 12 -> CyberAmber   // materially overconfident
                                        gap < -12 -> CyberCyan   // underclaiming
                                        else -> CyberEmerald
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "n=${bucket.settled}",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    }
                }

                // 2. SEARCH / FILTER BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by team, league, or slip ID...", color = TextMuted, fontSize = 12.sp) },
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
                            text = if (searchQuery.isNotEmpty()) "Try a different search term" else "Select matches on the home screen and start an AI prediction cycle. Your saved accumulator slips will be organized and available here to review anytime.",
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
                                if (isSlipExpanded) activeAccent.copy(alpha = 0.6f) else BorderColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_slip_${slip.slipId}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Top Header of Slip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Copy Slip Action
                                        IconButton(
                                            onClick = {
                                                copySlipToClipboard(context, slip)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = CyberCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        // Delete Single Slip Action
                                        IconButton(
                                            onClick = { slipToDelete = slip.slipId },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = TextMuted.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
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
                                                text = "${slip.jointProbability}% all land",
                                                color = CyberEmerald,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (slip.settledLegs > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = when (slip.outcome) {
                                                    com.example.models.BetOutcome.WON -> CyberEmerald.copy(alpha = 0.18f)
                                                    com.example.models.BetOutcome.LOST -> Color(0xFFFF5252).copy(alpha = 0.18f)
                                                    else -> Color(0xFF1E222D)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${slip.wonLegs}W-${slip.lostLegs}L of ${slip.settledLegs}",
                                                    color = when (slip.outcome) {
                                                        com.example.models.BetOutcome.WON -> CyberEmerald
                                                        com.example.models.BetOutcome.LOST -> Color(0xFFFF5252)
                                                        else -> TextMuted
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
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

                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Row: View Detailed View & Toggle Accordion
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
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = activeAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Open Dedicated Slip View",
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

                                // EXPANDED ACCORDION: Matches inside the slip
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
                                                border = BorderStroke(1.dp, Color(0xFF222836)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedReasons[reasonKey] = !isReasonExpanded }
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
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
                                                        Text(
                                                            text = item.simulatedOdds?.let { "@$it" } ?: "no price",
                                                            color = CyberGold,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

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
                                                                text = item.recommendedBet,
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
                text = { Text("This will permanently delete all saved bet slips from your history. This action cannot be undone.", color = TextMuted) },
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
                fontSize = 13.sp,
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
    sb.append("━━━━━━━━━━━━━━━━━━━\n")
    slip.items.forEachIndexed { i, item ->
        sb.append("${i + 1}. ${item.homeTeam} vs ${item.awayTeam}\n")
        sb.append("   ▶ Pick: ${item.recommendedBet} (${item.simulatedOdds?.let { "@$it" } ?: "no price"})\n")
        sb.append("   Confidence: ${item.confidence}%")
        if (item.outcome != com.example.models.BetOutcome.PENDING) {
            sb.append(" — ${item.outcome.name}")
            if (item.finalHomeScore != null && item.finalAwayScore != null) {
                sb.append(" (${item.finalHomeScore}-${item.finalAwayScore})")
            }
        }
        sb.append("\n")
    }
    sb.append("━━━━━━━━━━━━━━━━━━━\n")
    sb.append("Combined Odds: @${slip.totalCombinedOdds} | All legs land: ${slip.jointProbability}%\n")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("AI Bet Slip", sb.toString())
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Slip copied to clipboard!", Toast.LENGTH_SHORT).show()
}
