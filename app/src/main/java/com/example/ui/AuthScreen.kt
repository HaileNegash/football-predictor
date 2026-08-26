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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.UserTier
import com.example.viewmodel.PredictorViewModel
import kotlinx.coroutines.launch

private val CardBorder = Color(0xFF262A36)
private val TextMain = Color(0xFFECEFF1)
private val TextSub = Color(0xFF90A4AE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: PredictorViewModel,
    onNavigateBack: () -> Unit
) {
    val userManager = viewModel.userManager
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    val isLoading by userManager.isLoading.collectAsStateWithLifecycle()
    val customSettings by viewModel.customSettings.collectAsStateWithLifecycle()
    val activeAccent = customSettings.accentColorMode.color
    val activeBg = customSettings.themeMode.bgColor
    val activeCardBg = customSettings.themeMode.cardColor

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val isSignedIn = currentUser.email != "guest@footballpredictor.app"

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Create Account
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMeChecked by remember { mutableStateOf(true) }
    var autoSignInChecked by remember { mutableStateOf(true) }

    // Load saved remembered credentials on start
    LaunchedEffect(Unit) {
        val (savedEmail, savedPassword) = userManager.getRememberedCredentials()
        if (savedEmail.isNotBlank()) {
            emailInput = savedEmail
            passwordInput = savedPassword
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSignedIn) "User Account & Quota" else "Sign In / Register",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_auth_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = activeAccent
                        )
                    }
                },
                actions = {
                    // Tier Badge in Top Bar
                    Surface(
                        color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600).copy(alpha = 0.2f) else Color(0xFF262A36),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else CardBorder),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (currentUser.tier == UserTier.PRO_VIP) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD600),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = currentUser.tier.title,
                                color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else TextSub,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
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
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSignedIn) {
                // ==================== SIGNED IN VIEW ====================
                item {
                    // Profile Hero Card
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(activeAccent, Color(0xFF2979FF))
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser.displayName.take(1).uppercase(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentUser.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )

                            Text(
                                text = currentUser.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSub
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pro VIP vs Free Pill
                            Surface(
                                color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600).copy(alpha = 0.15f) else Color(0xFF1E222B),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (currentUser.tier == UserTier.PRO_VIP) Icons.Filled.Star else Icons.Filled.Shield,
                                        contentDescription = null,
                                        tint = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else activeAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${currentUser.tier.title} Member",
                                        color = if (currentUser.tier == UserTier.PRO_VIP) Color(0xFFFFD600) else TextMain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Daily AI Predictions Quota Dashboard
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = activeAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Today's Prediction Quota",
                                        fontWeight = FontWeight.Bold,
                                        color = TextMain,
                                        fontSize = 15.sp
                                    )
                                }

                                Text(
                                    text = if (currentUser.tier == UserTier.PRO_VIP) "UNLIMITED" else "${currentUser.remainingPredictions} left",
                                    color = if (currentUser.remainingPredictions > 0 || currentUser.tier == UserTier.PRO_VIP) Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (currentUser.tier == UserTier.FREE) {
                                val progress = (currentUser.dailyPredictionsUsed.toFloat() / currentUser.dailyLimit.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (progress >= 1f) Color(0xFFFF5252) else activeAccent,
                                    trackColor = Color(0xFF262A36)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${currentUser.dailyPredictionsUsed} used of ${currentUser.dailyLimit} limit",
                                        color = TextSub,
                                        fontSize = 11.sp
                                    )
                                    Text("Resets at 00:00 UTC", color = TextSub, fontSize = 11.sp)
                                }
                            } else {
                                Text(
                                    "✨ PRO VIP status active. You have full unlimited access to all AI prediction models and real-time deep analyses.",
                                    color = Color(0xFFFFD600),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item {
                    // Security & Auto Sign-In Controls
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Security & Session Settings",
                                fontWeight = FontWeight.Bold,
                                color = TextMain,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto sign in toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto Sign-In on Launch", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Automatically authenticate when app opens", color = TextSub, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = currentUser.isAutoSignedIn,
                                    onCheckedChange = { userManager.toggleAutoSignIn(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = activeAccent
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = CardBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Remember password toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Remember Credentials", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Keep credentials securely saved on device", color = TextSub, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = currentUser.rememberPassword,
                                    onCheckedChange = { userManager.toggleRememberPassword(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = activeAccent
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    // Sign Out Button
                    Button(
                        onClick = {
                            userManager.signOut {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Signed out. Switched to Guest Mode.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262A36)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Logout, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out to Guest Mode", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

            } else {
                // ==================== SIGN IN / GUEST VIEW ====================

                // 1. Google Smart Auto Sign-In Banner
                item {
                    Surface(
                        color = Color(0xFF1E222B),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF4285F4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("G", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color(0xFF4285F4))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Google Auto Sign-In",
                                        fontWeight = FontWeight.Bold,
                                        color = TextMain,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "1-tap instant sign in with Google Smart Lock",
                                        color = TextSub,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    // Trigger Google One-Tap / Auto Sign-In
                                    userManager.signInWithGoogleAuto(
                                        email = "liokingo2024@gmail.com",
                                        displayName = "Lio King",
                                        photoUrl = null
                                    ) { success, msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_google_sign_in")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Text("Continue with Google", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Email & Password Form with Tabs
                item {
                    Surface(
                        color = activeCardBg,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Tabs: Sign In vs Register
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color(0xFF1E222B),
                                contentColor = activeAccent,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                        color = activeAccent
                                    )
                                },
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Sign In", fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("Create Account", fontWeight = FontWeight.Bold) }
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Email Field
                            Text("Email Address", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                placeholder = { Text("you@example.com", color = TextSub.copy(alpha = 0.5f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Email, contentDescription = null, tint = activeAccent)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeAccent,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextMain,
                                    unfocusedTextColor = TextMain,
                                    focusedContainerColor = Color(0xFF1E222B),
                                    unfocusedContainerColor = Color(0xFF1E222B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_auth_email")
                            )

                            // Quick email domain chips
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val domains = listOf("@gmail.com", "@outlook.com", "@yahoo.com", "@icloud.com")
                                items(domains) { domain ->
                                    Surface(
                                        onClick = {
                                            val username = emailInput.substringBefore("@")
                                            emailInput = "$username$domain"
                                        },
                                        color = Color(0xFF1E222B),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, CardBorder)
                                    ) {
                                        Text(
                                            text = domain,
                                            color = TextSub,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Password Field
                            Text("Password", color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                placeholder = { Text("••••••••", color = TextSub.copy(alpha = 0.5f)) },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = activeAccent)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Toggle Password",
                                            tint = TextSub
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeAccent,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextMain,
                                    unfocusedTextColor = TextMain,
                                    focusedContainerColor = Color(0xFF1E222B),
                                    unfocusedContainerColor = Color(0xFF1E222B)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_auth_password")
                            )

                            // Password strength bar if creating account
                            if (selectedTab == 1 && passwordInput.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val strengthScore = when {
                                    passwordInput.length >= 10 && passwordInput.any { it.isDigit() } && passwordInput.any { !it.isLetterOrDigit() } -> 3
                                    passwordInput.length >= 6 -> 2
                                    else -> 1
                                }
                                val (strengthColor, strengthLabel) = when (strengthScore) {
                                    3 -> Pair(Color(0xFF00E676), "Strong Password")
                                    2 -> Pair(Color(0xFFFFD600), "Medium Password")
                                    else -> Pair(Color(0xFFFF5252), "Weak (min 6 chars)")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    LinearProgressIndicator(
                                        progress = { strengthScore / 3f },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = strengthColor,
                                        trackColor = Color(0xFF262A36)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(strengthLabel, color = strengthColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Remember Me & Auto Sign-In Checkboxes
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { rememberMeChecked = !rememberMeChecked }
                            ) {
                                Checkbox(
                                    checked = rememberMeChecked,
                                    onCheckedChange = { rememberMeChecked = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = activeAccent,
                                        uncheckedColor = TextSub
                                    )
                                )
                                Text("Remember Password & Auto-Fill", color = TextMain, fontSize = 12.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { autoSignInChecked = !autoSignInChecked }
                            ) {
                                Checkbox(
                                    checked = autoSignInChecked,
                                    onCheckedChange = { autoSignInChecked = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = activeAccent,
                                        uncheckedColor = TextSub
                                    )
                                )
                                Text("Auto Sign-In Next Time", color = TextMain, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Submit Button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    userManager.signInWithEmailPassword(
                                        email = emailInput,
                                        password = passwordInput,
                                        rememberMe = rememberMeChecked
                                    ) { success, msg ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = activeAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_auth_submit")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Text(
                                        if (selectedTab == 0) "Sign In Securely" else "Create Account & Start",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Guest Mode Card
                item {
                    Surface(
                        color = Color(0xFF1E222B),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Guest Mode Active", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Limited to ${currentUser.dailyLimit} free AI predictions per day", color = TextSub, fontSize = 11.sp)
                            }

                            Surface(
                                color = Color(0xFF262A36),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${currentUser.remainingPredictions} Left Today",
                                    color = activeAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
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
}
