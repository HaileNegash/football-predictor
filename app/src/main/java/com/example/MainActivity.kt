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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
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
                onNavigateToConfig = { navController.navigate("prediction_config") }
            )
        }
        composable("prediction_config") {
            PredictionConfigScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onPredict = { /* AI Prediction action */ }
            )
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
fun HomeScreen(viewModel: PredictorViewModel, onNavigateToSettings: () -> Unit, onNavigateToConfig: () -> Unit) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val currentDate by viewModel.currentDate.collectAsStateWithLifecycle()
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
                    IconButton(onClick = { viewModel.fetchFixtures(forceRefresh = true) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
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
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    
    // Extract selected matches from selectedItems set
    var isBetTypesExpanded by remember { mutableStateOf(false) }
    val selectedMatchIds = selectedItems.filter { it.startsWith("match_") }.mapNotNull { it.removePrefix("match_").toIntOrNull() }.toSet()
    val selectedMatches = countries.flatMap { it.leagues }.flatMap { it.matches }.filter { it.id in selectedMatchIds }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prediction Config", color = TextMain, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppDarkBg, titleContentColor = TextMain)
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (selectedMatches.isNotEmpty() && selectedBetTypes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onPredict,
                    containerColor = AccentOrange,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Outlined.StarOutline, "Predict") },
                    text = { Text("Generate Predictions", fontWeight = FontWeight.Bold) }
                )
            }
        },
        containerColor = AppDarkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
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
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Selected Matches (${selectedMatches.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            item {
                Surface(
                    color = CardDarkBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
                        if (selectedMatches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No matches selected", color = TextSub)
                            }
                        } else {
                            selectedMatches.forEachIndexed { index, match ->
                                MatchItem(
                                    match = match,
                                    isSelected = selectedItems.contains("match_${match.id}"),
                                    onToggleSelect = { viewModel.toggleSearchItemSelection("match_${match.id}") }
                                )
                                if (index < selectedMatches.size - 1) {
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
                title = { Text("Settings", color = TextMain, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AccentOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppDarkBg,
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
            Surface(
                color = CardDarkBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = TextSub,
                            cursorColor = AccentOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = {
                            viewModel.saveApiFootballKey(keyInput)
                            onNavigateBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("SAVE KEY", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
