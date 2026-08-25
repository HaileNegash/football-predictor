package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Settings
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.models.Country
import com.example.models.Match
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PredictorViewModel

val AppDarkBg = Color(0xFF030914)
val HeaderBg = Color(0xFF001535)
val DividerColor = Color(0xFF0C1B33)
val TextMain = Color(0xFFF0F2F5)
val TextSub = Color(0xFF8B9CB6)
val AccentBlue = Color(0xFF1E63D6)
val PredictBg = Color(0xFF0E223D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PredictorApp()
            }
        }
    }
}

@Composable
fun PredictorApp(viewModel: PredictorViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(viewModel = viewModel, onNavigateToSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PredictorViewModel, onNavigateToSettings: () -> Unit) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    var expandedLeagues by remember { mutableStateOf(setOf<Int>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Football Predictor", color = TextMain) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderBg,
                    titleContentColor = TextMain
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = TextMain
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = AppDarkBg
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(AppDarkBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().background(AppDarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage ?: "", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToSettings, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                        Text("Configure API Key")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppDarkBg)
                    .padding(innerPadding)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PredictBg)
                            .padding(vertical = 4.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.changeDateBy(-1) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Day",
                                tint = AccentBlue
                            )
                        }
                        val displayText = if (viewModel.isToday) "TODAY • $currentDate" else currentDate
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.changeDateBy(1) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Day",
                                tint = AccentBlue
                            )
                        }
                    }
                }
                
                countries.forEach { country ->
                    country.leagues.forEach { league ->
                        val isExpanded = expandedLeagues.contains(league.id)
                        item(key = "header_${league.id}") {
                            LeagueHeader(
                                league = league,
                                country = country,
                                isCollapsed = !isExpanded,
                                onToggleCollapse = {
                                    expandedLeagues = if (isExpanded) {
                                        expandedLeagues - league.id
                                    } else {
                                        expandedLeagues + league.id
                                    }
                                }
                            )
                        }
                        if (isExpanded) {
                            items(league.matches, key = { it.id }) { match ->
                                MatchItem(
                                    match = match,
                                    onPredictClick = { viewModel.predictMatch(match.id) }
                                )
                                HorizontalDivider(color = DividerColor, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit
) {
    val currentKey by viewModel.apiFootballKey.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf(currentKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextMain) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextMain
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderBg,
                    titleContentColor = TextMain
                )
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = AppDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "API Management",
                style = MaterialTheme.typography.titleMedium,
                color = TextMain,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Enter your API-Football API key. This will be used to fetch live fixtures and match data.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSub,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API-Football Key", color = TextSub) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSub,
                    cursorColor = AccentBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    viewModel.saveApiFootballKey(keyInput)
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("SAVE KEY", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LeagueHeader(
    league: com.example.models.League,
    country: Country,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .clickable { onToggleCollapse() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.StarOutline,
            contentDescription = "Favorite",
            tint = TextSub,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        
        if (country.flagUrl != null) {
            AsyncImage(
                model = country.flagUrl,
                contentDescription = "${country.name} Flag",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 18.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 12.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = league.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMain,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    tint = AccentBlue,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = country.name.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSub
            )
        }
        
        Icon(
            imageVector = Icons.Filled.AccountTree,
            contentDescription = "Tournament Bracket",
            tint = TextSub,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (isCollapsed) "Expand" else "Collapse",
            tint = AccentBlue,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun MatchItem(match: Match, onPredictClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppDarkBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.StarOutline,
                contentDescription = "Favorite",
                tint = TextSub,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Home Team
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.homeLogo != null) {
                        AsyncImage(
                            model = match.homeLogo,
                            contentDescription = match.homeTeam,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.LightGray, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.homeTeam,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Away Team
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (match.awayLogo != null) {
                        AsyncImage(
                            model = match.awayLogo,
                            contentDescription = match.awayTeam,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.Gray, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.awayTeam,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = match.startTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSub
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                if (match.prediction != null) {
                    Text(
                        text = "PREDICTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .border(1.dp, TextSub.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .clickable { onPredictClick() }
                            .testTag("predict_button_${match.id}")
                    ) {
                        Text(
                            text = "PREDICT",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSub,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        if (match.prediction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = PredictBg,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "AI Prediction: ${match.prediction!!.recommendedBet}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMain,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${match.prediction!!.confidence}% Conf.",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.prediction!!.rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSub
                    )
                }
            }
        }
    }
}
