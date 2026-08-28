package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.models.Country
import com.example.models.Match
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PredictorViewModel
import java.util.Locale

// Exact Theme Colors from Screenshot
val AppDarkBg = Color(0xFF0D0E11)         // Dark background
val CardDarkBg = Color(0xFF181A20)        // Dark elevated container
val SearchInputBg = Color(0xFF131418)     // Inner search background
val DividerColor = Color(0xFF23262F)      // Dark dividers
val TextMain = Color(0xFFFFFFFF)          // Crisp white text
val TextSub = Color(0xFF8E929D)           // Muted secondary text
val AccentOrange = Color(0xFFF36621)      // Vivid Sports Orange
val AccentGreen = Color(0xFF00E676)       // Vivid Emerald Green
val UnselectedIndicator = Color(0xFF484C58) // Circle unselected border
val PredictBg = Color(0xFF251A14)         // Deep orange tint for prediction cards

fun getCountryFlagEmoji(countryName: String): String {
    return when (countryName.lowercase(Locale.getDefault()).trim()) {
        "england" -> "🏴󠁧󠁢󠁥󠁮󠁧󠁿"
        "spain" -> "🇪🇸"
        "germany" -> "🇩🇪"
        "world" -> "🌐"
        "argentina" -> "🇦🇷"
        "armenia" -> "🇦🇲"
        "australia" -> "🇦🇺"
        "bolivia" -> "🇧🇴"
        "brazil" -> "🇧🇷"
        "italy" -> "🇮🇹"
        "france" -> "🇫🇷"
        "portugal" -> "🇵🇹"
        "netherlands" -> "🇳🇱"
        "belgium" -> "🇧🇪"
        "turkey" -> "🇹🇷"
        "scotland" -> "🏴󠁧󠁢󠁳󠁣󠁴󠁿"
        "ireland" -> "🇮🇪"
        "usa", "united states" -> "🇺🇸"
        "mexico" -> "🇲🇽"
        "japan" -> "🇯🇵"
        "saudi arabia" -> "🇸🇦"
        "egypt" -> "🇪🇬"
        "morocco" -> "🇲🇦"
        "croatia" -> "🇭🇷"
        "switzerland" -> "🇨🇭"
        "austria" -> "🇦🇹"
        "denmark" -> "🇩🇰"
        "sweden" -> "🇸🇪"
        "norway" -> "🇳🇴"
        "poland" -> "🇵🇱"
        "colombia" -> "🇨🇴"
        "chile" -> "🇨🇱"
        "uruguay" -> "🇺🇾"
        else -> "⚽"
    }
}

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
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("bets_history") },
                onNavigateToAuth = { navController.navigate("auth") },
                onNavigateToConfig = { navController.navigate("prediction_config") }
            )
        }
        composable("bets_history") {
            com.example.ui.BetsHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onViewSlipDetails = { navController.navigate("predicted_bets_result") }
            )
        }
        composable("auth") {
            com.example.ui.AuthScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("prediction_config") {
            PredictionConfigScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onPredict = { navController.navigate("agent_prediction") }
            )
        }
        composable("agent_prediction") {
            com.example.ui.AgentPredictionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onShowBets = {
                    viewModel.saveAndBuildSlip()
                    navController.navigate("predicted_bets_result")
                }
            )
        }
        composable("predicted_bets_result") {
            com.example.ui.PredictedBetsResultScreen(
                viewModel = viewModel,
                onCloseToHome = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate("auth") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PredictorViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var expandedCountries by remember(countries) { 
        mutableStateOf(emptySet<String>()) 
    }

    val selectedItemsForFab by viewModel.selectedSearchItems.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            val matchCount = selectedItemsForFab.count { it.startsWith("match_") }
            if (matchCount > 0) {
                Surface(
                    onClick = { onNavigateToConfig() },
                    color = AccentOrange,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Ready to predict", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha=0.85f))
                            Text("${matchCount} Matches Selected", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Next", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = Color.White)
                        }
                    }
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Football Predictor", 
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateToAuth,
                        modifier = Modifier.testTag("btn_top_account")
                    ) {
                        if (currentUser.email != "guest@footballpredictor.app") {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (currentUser.tier == com.example.models.UserTier.PRO_VIP) Color(0xFFFFD600) else AccentOrange,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser.displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "Account & Sign In",
                                tint = AccentOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("btn_top_history")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Bets History",
                            tint = AccentOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("btn_top_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = AccentOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = AppDarkBg,
                    titleContentColor = TextMain
                )
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = AppDarkBg
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(AppDarkBg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().background(AppDarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage ?: "", color = Color(0xFFFF5252), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToSettings, colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                        Text("Configure API Key", color = Color.White)
                    }
                }
            }
        } else {
            val focusManager = LocalFocusManager.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppDarkBg)
                    .padding(innerPadding)
            ) {
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                
                // Date Bar matching screenshot exactly
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isPreviousEnabled = !viewModel.isToday
                        IconButton(
                            onClick = { viewModel.changeDateBy(-1) },
                            enabled = isPreviousEnabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Day",
                                tint = if (isPreviousEnabled) AccentOrange else Color(0xFF484C58),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        val displayText = if (viewModel.isToday) "Today - $currentDate" else currentDate
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMain,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        
                        val isNextDayEnabled by viewModel.isNextDayEnabled.collectAsStateWithLifecycle()
                        IconButton(
                            onClick = { viewModel.changeDateBy(1) },
                            enabled = isNextDayEnabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Day",
                                tint = if (isNextDayEnabled) AccentOrange else Color(0xFF484C58),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                // Search Input with Orange Border matching screenshot exactly
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(48.dp)
                        .background(SearchInputBg, RoundedCornerShape(14.dp))
                        .border(1.5.dp, AccentOrange, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = TextSub,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            textStyle = TextStyle(
                                color = TextMain,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(AccentOrange),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search country, league, or team",
                                        color = TextSub.copy(alpha = 0.8f),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
                val selectedItemsList by viewModel.selectedSearchItems.collectAsStateWithLifecycle()

                if (searchQuery.isNotBlank()) {
                    if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found", color = TextSub)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            item {
                                Surface(
                                    color = CardDarkBg,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        searchResults.forEachIndexed { index, item ->
                                            val isSelected = selectedItemsList.contains(item.id)
                                            SearchItemRow(
                                                item = item,
                                                searchQuery = searchQuery,
                                                isSelected = isSelected,
                                                onToggle = { viewModel.toggleSearchItemSelection(item.id) }
                                            )
                                            if (index < searchResults.size - 1) {
                                                HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp)
                    ) {
                        item {
                            Surface(
                                color = CardDarkBg,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    countries.forEachIndexed { index, country ->
                                        val isCountryExpanded = expandedCountries.contains(country.name)
                                        CountryHeader(
                                            country = country,
                                            isCollapsed = !isCountryExpanded,
                                            isSelected = selectedItemsList.contains("country_${country.name}"),
                                            onToggleSelect = { viewModel.toggleSearchItemSelection("country_${country.name}") },
                                            onToggleCollapse = {
                                                expandedCountries = if (isCountryExpanded) {
                                                    expandedCountries - country.name
                                                } else {
                                                    expandedCountries + country.name
                                                }
                                            }
                                        )
                                        
                                        if (isCountryExpanded) {
                                            country.leagues.forEach { league ->
                                                LeagueHeader(
                                                    league = league,
                                                    isSelected = selectedItemsList.contains("league_${league.id}"),
                                                    onToggleSelect = { viewModel.toggleSearchItemSelection("league_${league.id}") }
                                                )
                                                league.matches.forEach { match ->
                                                    MatchItem(
                                                        match = match,
                                                        isSelected = selectedItemsList.contains("match_${match.id}"),
                                                        onToggleSelect = { viewModel.toggleSearchItemSelection("match_${match.id}") }
                                                    )
                                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                                }
                                            }
                                        }
                                        
                                        if (index < countries.size - 1) {
                                            HorizontalDivider(color = DividerColor, thickness = 1.dp)
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

@Composable
fun CustomRadioIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = AccentOrange,
    unselectedColor: Color = UnselectedIndicator
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .border(
                width = 2.dp,
                color = if (isSelected) selectedColor else unselectedColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(selectedColor, CircleShape)
            )
        }
    }
}

@Composable
fun CountryHeader(
    country: Country,
    isCollapsed: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleCollapse() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clickable { onToggleSelect() }
                .padding(2.dp)
        ) {
            CustomRadioIndicator(isSelected = isSelected)
        }
        Spacer(modifier = Modifier.width(14.dp))
        
        if (country.name.equals("World", ignoreCase = true)) {
            Text("🌐", fontSize = 18.sp)
        } else if (country.flagUrl != null && !country.flagUrl.endsWith(".svg")) {
            AsyncImage(
                model = country.flagUrl,
                contentDescription = "${country.name} Flag",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        } else {
            Text(getCountryFlagEmoji(country.name), fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Text(
            text = country.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextMain,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = if (isCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = if (isCollapsed) "Expand" else "Collapse",
            tint = AccentOrange,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun LeagueHeader(league: com.example.models.League, isSelected: Boolean, onToggleSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppDarkBg.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = league.name,
            style = MaterialTheme.typography.labelLarge,
            color = AccentOrange,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clickable { onToggleSelect() }
                .padding(2.dp)
        ) {
            CustomRadioIndicator(isSelected = isSelected)
        }
    }
}

@Composable
fun MatchItem(match: Match, isSelected: Boolean, onToggleSelect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDarkBg)
            .clickable { onToggleSelect() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomRadioIndicator(isSelected = isSelected)
            Spacer(modifier = Modifier.width(14.dp))
            
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
            }
        }
        
        if (match.prediction != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = PredictBg,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f)),
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
                            color = AccentOrange,
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

@Composable
fun SearchItemRow(item: com.example.models.SearchItem, searchQuery: String, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.logoUrl != null && !item.logoUrl.endsWith(".svg")) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(getCountryFlagEmoji(item.name), fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            HighlightedText(
                text = item.name,
                query = searchQuery,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMain,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSub
            )
        }
        CustomRadioIndicator(isSelected = isSelected)
    }
}

@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (query.isBlank()) {
        Text(text = text, style = style, color = color, fontWeight = fontWeight, modifier = modifier, maxLines = maxLines, overflow = overflow)
        return
    }
    
    val startIndex = text.indexOf(query, ignoreCase = true)
    
    if (startIndex >= 0) {
        val annotatedString = buildAnnotatedString {
            if (startIndex > 0) {
                withStyle(style = SpanStyle(color = TextSub)) {
                    append(text.substring(0, startIndex))
                }
            }
            withStyle(style = SpanStyle(color = AccentOrange, fontWeight = FontWeight.Bold)) {
                append(text.substring(startIndex, startIndex + query.length))
            }
            if (startIndex + query.length < text.length) {
                withStyle(style = SpanStyle(color = TextSub)) {
                    append(text.substring(startIndex + query.length))
                }
            }
        }
        Text(text = annotatedString, style = style, fontWeight = fontWeight, modifier = modifier, maxLines = maxLines, overflow = overflow)
    } else {
        Text(text = text, style = style, color = TextSub.copy(alpha = 0.5f), fontWeight = fontWeight, modifier = modifier, maxLines = maxLines, overflow = overflow)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionConfigScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onPredict: () -> Unit
) {
    val selectedItems by viewModel.selectedSearchItems.collectAsStateWithLifecycle()
    val availableBetTypes = viewModel.availableBetTypes
    val selectedBetTypes by viewModel.selectedBetTypes.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val availableCurrencies = viewModel.availableCurrencies
    val budget by viewModel.budget.collectAsStateWithLifecycle()
    val moneyRange by viewModel.moneyRange.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    
    // Extract selected matches from selectedItems set
    var isBetTypesExpanded by remember { mutableStateOf(false) }
    var isCurrencyListExpanded by remember { mutableStateOf(false) }
    val selectedMatchIds = selectedItems.filter { it.startsWith("match_") }.mapNotNull { it.removePrefix("match_").toIntOrNull() }.toSet()
    val selectedMatches = countries.flatMap { it.leagues }.flatMap { it.matches }.filter { it.id in selectedMatchIds }
    
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Prediction Config", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("AI Strategy & Market Calibration", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                    }
                },
                actions = {
                    Surface(
                        color = Color(0xFF1E222B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Engine Ready", color = TextSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppDarkBg, titleContentColor = TextMain)
            )
        },
        bottomBar = {
            Surface(
                color = CardDarkBg,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, DividerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = onPredict,
                        enabled = selectedBetTypes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            disabledContainerColor = Color(0xFF2E313C)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_run_ai_predictions")
                    ) {
                        Text(
                            text = if (selectedMatches.isNotEmpty()) {
                                "Run AI Predictions (${selectedMatches.size} Matches)"
                            } else {
                                "Run AI Predictions"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedBetTypes.isNotEmpty()) Color.White else TextSub
                        )
                    }
                    if (selectedBetTypes.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please select at least one betting prediction type above",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5252),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        containerColor = AppDarkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBetTypesExpanded = !isBetTypesExpanded }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Betting Prediction Types",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextMain,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isBetTypesExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Toggle Betting Types",
                                tint = AccentOrange
                            )
                        }

                        if (isBetTypesExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.selectAllBetTypes() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Select All", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.deselectAllBetTypes() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E313C)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Deselect All", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
            
            if (isBetTypesExpanded) {
                item {
                    Surface(
                        color = CardDarkBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column {
                            availableBetTypes.forEachIndexed { index, betType ->
                                val isSelected = selectedBetTypes.contains(betType)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleBetType(betType) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CustomRadioIndicator(isSelected = isSelected)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = betType,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) TextMain else TextSub
                                    )
                                }
                                if (index < availableBetTypes.size - 1) {
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Currency Selector Section
            item {
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCurrencyListExpanded = !isCurrencyListExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Currency Selection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select your preferred betting currency",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSub
                                )
                            }
                            Surface(
                                color = AccentOrange.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedCurrency.flagEmoji} ${selectedCurrency.code} (${selectedCurrency.symbol})",
                                        color = AccentOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isCurrencyListExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Toggle Currencies",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick horizontal chips for top currencies
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableCurrencies) { curr ->
                                val isSelected = curr.code == selectedCurrency.code
                                Surface(
                                    onClick = { viewModel.selectCurrency(curr) },
                                    color = if (isSelected) AccentOrange else Color(0xFF23262F),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = curr.flagEmoji, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${curr.code} (${curr.symbol})",
                                            color = if (isSelected) Color.White else TextSub,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isCurrencyListExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = DividerColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = "All Currencies",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSub,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                availableCurrencies.forEach { curr ->
                                    val isSelected = curr.code == selectedCurrency.code
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) AccentOrange.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable { viewModel.selectCurrency(curr) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(curr.flagEmoji, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = curr.name,
                                                    color = if (isSelected) TextMain else TextSub,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "${curr.code} • ${curr.symbol}",
                                                    color = if (isSelected) AccentOrange else TextSub.copy(alpha = 0.7f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = AccentOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Budget (Stake / Bankroll) Setter Section
            item {
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Betting Budget / Stake",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Set your total stake or bankroll limit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSub
                                )
                            }
                            Surface(
                                color = AccentOrange.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${selectedCurrency.symbol} ${budget.toInt()}",
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Slider(
                            value = budget,
                            onValueChange = { viewModel.updateBudget(it) },
                            valueRange = 5f..1000f,
                            steps = 198,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentOrange,
                                activeTrackColor = AccentOrange,
                                inactiveTrackColor = Color(0xFF2E313C)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Quick Stake Presets",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSub,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val budgetPresets = listOf(10f, 25f, 50f, 100f, 250f, 500f)
                            budgetPresets.forEach { presetVal ->
                                val isSelected = budget.toInt() == presetVal.toInt()
                                Surface(
                                    onClick = { viewModel.updateBudget(presetVal) },
                                    color = if (isSelected) AccentOrange else Color(0xFF23262F),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${selectedCurrency.symbol}${presetVal.toInt()}",
                                        color = if (isSelected) Color.White else TextSub,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Money Target Setter Section
            item {
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Money Target Setter",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextMain,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = AccentOrange.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${selectedCurrency.symbol}${moneyRange.start.toInt()} - ${selectedCurrency.symbol}${moneyRange.endInclusive.toInt()}",
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set your desired minimum and maximum target profit / payout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSub
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Target indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Min Target", style = MaterialTheme.typography.labelSmall, color = TextSub)
                                Text(
                                    text = "${selectedCurrency.symbol}${moneyRange.start.toInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Max Target", style = MaterialTheme.typography.labelSmall, color = TextSub)
                                Text(
                                    text = "${selectedCurrency.symbol}${moneyRange.endInclusive.toInt()}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        RangeSlider(
                            value = moneyRange,
                            onValueChange = { newRange ->
                                viewModel.updateMoneyRange(newRange)
                            },
                            valueRange = 5f..1000f,
                            steps = 198,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentOrange,
                                activeTrackColor = AccentOrange,
                                inactiveTrackColor = Color(0xFF2E313C)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Quick Presets",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSub,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                "10 - 50" to (10f..50f),
                                "25 - 100" to (25f..100f),
                                "50 - 250" to (50f..250f),
                                "100 - 500" to (100f..500f)
                            )
                            presets.forEach { (label, range) ->
                                val isSelected = (moneyRange.start.toInt() == range.start.toInt() && moneyRange.endInclusive.toInt() == range.endInclusive.toInt())
                                Surface(
                                    onClick = { viewModel.updateMoneyRange(range) },
                                    color = if (isSelected) AccentOrange else Color(0xFF23262F),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${selectedCurrency.symbol}$label",
                                        color = if (isSelected) Color.White else TextSub,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
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

@Composable
fun SettingsScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    com.example.ui.SettingsScreen(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToAuth = onNavigateToAuth
    )
}
