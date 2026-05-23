package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

// --- PARTICLE EMISSIONS MODELS FOR HIGH-POLISH EFFECTS ---
data class FloatingEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val initialXHex: Float, // relative to container
    var yOffset: Float = 0f,
    var xOffset: Float = 0f,
    var alpha: Float = 1.0f,
    var scale: Float = 1.0f,
    val driftSpeedX: Float = (-20..20).random() / 15f
)

data class FallingEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val initialX: Float, // x percent (0..1)
    var yPercent: Float = -0.1f,
    val speed: Float = (15..35).random() / 1000f,
    val rotation: Float = (-15..15).random().toFloat()
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: SecretViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // VM state observations
    val secretsList by viewModel.approvedSecrets.collectAsStateWithLifecycle()
    val allSecretsAdmin by viewModel.allSecretsAdmin.collectAsStateWithLifecycle()
    val secretOfDay by viewModel.secretOfDay.collectAsStateWithLifecycle()
    val selectedSecret by viewModel.selectedSecret.collectAsStateWithLifecycle()
    val comments by viewModel.commentsForSelected.collectAsStateWithLifecycle()
    val bannedIps by viewModel.bannedIps.collectAsStateWithLifecycle()
    val userReactions by viewModel.userReactions.collectAsStateWithLifecycle()
    val isPosting by viewModel.isPostingInProgress.collectAsStateWithLifecycle()
    val alertMsg by viewModel.alertEvent.collectAsStateWithLifecycle()
    val karmaScore by viewModel.karmaScore.collectAsStateWithLifecycle()
    val streakCount by viewModel.streakCount.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentFeedMode.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()

    // Internal navigation / overlays
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAdminConsole by remember { mutableStateOf(false) }
    var brandClickCount by remember { mutableStateOf(0) }
    var activeCategoryFilter by remember { mutableStateOf("All") }
    var commentTextState by remember { mutableStateOf("") }
    var replyToId by remember { mutableStateOf<Long?>(null) }
    
    // Battles and anonymous event state
    val pollAApproved = remember { mutableStateOf(0) }
    val pollBApproved = remember { mutableStateOf(0) }
    val userPolledOption = remember { mutableStateOf<String?>(null) }

    // Particle state containers for floating graphics
    var floatingList by remember { mutableStateOf(listOf<FloatingEmoji>()) }
    var fallingList by remember { mutableStateOf(listOf<FallingEmoji>()) }

    // Tick falling and rising graphics
    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            if (floatingList.isNotEmpty()) {
                floatingList = floatingList.map { emoji ->
                    emoji.copy(
                        yOffset = emoji.yOffset - 4f,
                        xOffset = emoji.xOffset + emoji.driftSpeedX,
                        scale = (emoji.scale - 0.005f).coerceAtLeast(0.3f),
                        alpha = (emoji.alpha - 0.012f).coerceAtLeast(0.0f)
                    )
                }.filter { it.alpha > 0.05f }
            }
            if (fallingList.isNotEmpty()) {
                fallingList = fallingList.map { emoji ->
                    emoji.copy(yPercent = emoji.yPercent + emoji.speed)
                }.filter { it.yPercent < 1.1f }
            }
        }
    }

    // Capture particle rain launches
    LaunchedEffect(Unit) {
        viewModel.emojiRainTrigger.collect { reactionType ->
            val emojiChar = getEmojiForReactionType(reactionType)
            val countRain = 35
            val batch = (0 until countRain).map {
                FallingEmoji(
                    emoji = emojiChar,
                    initialX = (0..100).random() / 100f,
                    yPercent = -((5..150).random() / 100f) // stagger entry coordinates
                )
            }
            fallingList = fallingList + batch
        }
    }

    // Floating alert message controller
    LaunchedEffect(alertMsg) {
        if (alertMsg != null) {
            delay(4000)
            viewModel.dismissAlert()
        }
    }

    // Setup beautiful high-fidelity radial background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicBackground)
            .drawBehind {
                // Procedural neon violet overlay at Top Right and hot magenta bloom at Bottom Left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricPurple.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.9f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPink.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(0f, size.height),
                        radius = size.width * 1.1f
                    )
                )
            }
    ) {
        // --- BASE SCREEN SHELL CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // AESTHETIC TOP BRAND HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Double Action Brand title (Secret admin access)
                Column(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        brandClickCount++
                        if (brandClickCount >= 3) {
                            showAdminConsole = !showAdminConsole
                            brandClickCount = 0
                            val targetMode = if (showAdminConsole) "ADMIN" else "ALL"
                            viewModel.changeFeedMode(targetMode)
                            viewModel.triggerEmojiRain("fire")
                        }
                    }
                ) {
                    Text(
                        text = "AURA",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = 4.sp,
                        color = NeonPink,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(
                                color = NeonPink.copy(alpha = 0.5f),
                                offset = Offset(0f, 0f),
                                blurRadius = 12f
                            )
                        )
                    )
                    Text(
                        text = "ANONYMOUS BROADCAST",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = TextMuted
                    )
                }

                // Gamification Status Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streaks Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicSurfaceVariant)
                            .border(1.dp, AmberGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔥", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${streakCount}d",
                            color = AmberGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Karma Score Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicSurfaceVariant)
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤫", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${karmaScore} XP",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // NAVIGATION TAB RECTANGLE SLIDER (M3 standard alignment but beautiful neon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmicSurface)
                    .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    "All" to "ALL",
                    "Swipe" to "TIKTOK",
                    "Battles" to "BATTLES",
                    "Saved" to "BOOKMARKS"
                )
                
                tabs.forEach { (label, mode) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CosmicSurfaceVariant else Color.Transparent)
                            .clickable {
                                viewModel.changeFeedMode(mode)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CyberCyan else TextGray,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Append notification dot if admin queue contains item
                if (showAdminConsole || allSecretsAdmin.any { it.moderationStatus == "PENDING" }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (currentMode == "ADMIN") ElectricPurple else Color.Transparent)
                            .clickable {
                                viewModel.changeFeedMode("ADMIN")
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡️", fontSize = 12.sp)
                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeonPink)
                            )
                        }
                    }
                }
            }

            // FILTER CAROUSEL FOR ALL / DETAILED STREAM (ONLY UNDER 'ALL')
            if (currentMode == "ALL") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("All", "Love", "Guilt", "Regret", "Mystery", "Work", "Fear", "Hope")
                    categories.forEach { cat ->
                        val isSelected = activeCategoryFilter == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonPink.copy(alpha = 0.2f) else CosmicSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonPink else CosmicBorder,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { activeCategoryFilter = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) TextWhite else TextGray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // FILTER CAROUSEL FOR ALL / DETAILED STREAM (ONLY UNDER 'ALL') -> Insert Trending Ticket here
            if (currentMode == "ALL") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeonPink.copy(alpha = 0.08f))
                        .border(1.dp, NeonPink.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 TRENDING NOW",
                        color = NeonPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Karma: +12,402",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- MAIN FEED BODY DECK ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentMode) {
                    "ALL" -> {
                        val filteredList = secretsList.filter {
                            activeCategoryFilter == "All" || it.category.equals(activeCategoryFilter, ignoreCase = true)
                        }

                        if (filteredList.isEmpty()) {
                            EmptyStatePlaceholder("No dark secrets whispered here yet.\nTap below to be the first.")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("secrets_feed_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Pin Secret of the Day on Top!
                                val sod = secretOfDay ?: secretsList.firstOrNull { it.isSecretOfDay }
                                if (sod != null && activeCategoryFilter == "All") {
                                    item {
                                        Text(
                                            text = "🏆 SECRET OF THE DAY",
                                            color = AmberGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        SecretConfessionCard(
                                            secret = sod,
                                            viewModel = viewModel,
                                            isPinnedStyling = true,
                                            userReactions = userReactions,
                                            isBookmarked = bookmarkedIds.contains(sod.id),
                                            onTriggerFloatingParticle = { emoji ->
                                                val randX = (40..60).random().toFloat()
                                                floatingList = floatingList + FloatingEmoji(emoji = emoji, initialXHex = randX)
                                            }
                                        )
                                        Divider(
                                            color = CosmicBorder,
                                            thickness = 1.dp,
                                            modifier = Modifier.padding(vertical = 16.dp)
                                        )
                                    }
                                }

                                items(filteredList.filter { sod?.id != it.id }) { secret ->
                                    SecretConfessionCard(
                                        secret = secret,
                                        viewModel = viewModel,
                                        isPinnedStyling = false,
                                        userReactions = userReactions,
                                        isBookmarked = bookmarkedIds.contains(secret.id),
                                        onTriggerFloatingParticle = { emoji ->
                                            val randX = (40..60).random().toFloat()
                                            floatingList = floatingList + FloatingEmoji(emoji = emoji, initialXHex = randX)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "TIKTOK" -> {
                        // IMMERSIVE FULL-SCREEN SWIPE MODE
                        if (secretsList.isEmpty()) {
                            EmptyStatePlaceholder("Nothing to swipe yet.")
                        } else {
                            TikTokSwipeDeck(
                                secrets = secretsList,
                                viewModel = viewModel,
                                userReactions = userReactions,
                                bookmarkedIds = bookmarkedIds,
                                onTriggerFloatingParticle = { emoji ->
                                    val randX = (30..70).random().toFloat()
                                    floatingList = floatingList + FloatingEmoji(emoji = emoji, initialXHex = randX)
                                }
                            )
                        }
                    }

                    "BATTLES" -> {
                        // GAMIFIED SECRET BATTLES PANEL
                        ConfessionBattlesPanel(
                            pollAApproved = pollAApproved,
                            pollBApproved = pollBApproved,
                            userPolledOption = userPolledOption,
                            onVote = { opt ->
                                viewModel.triggerEmojiRain(if (opt == "A") "love" else "fire")
                            }
                        )
                    }

                    "BOOKMARKS" -> {
                        val bookmarkedSecrets = secretsList.filter { bookmarkedIds.contains(it.id) }
                        if (bookmarkedSecrets.isEmpty()) {
                            EmptyStatePlaceholder("Your vault is empty. Bookmark confessions to read offline later.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(bookmarkedSecrets) { secret ->
                                    SecretConfessionCard(
                                        secret = secret,
                                        viewModel = viewModel,
                                        isPinnedStyling = false,
                                        userReactions = userReactions,
                                        isBookmarked = true,
                                        onTriggerFloatingParticle = { emoji ->
                                            floatingList = floatingList + FloatingEmoji(emoji = emoji, initialXHex = 50f)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    "ADMIN" -> {
                        // ADMINISTRATOR CONTROL STATION
                        AdminModerationConsole(
                            allSecretsAndFlagged = allSecretsAdmin,
                            bannedIps = bannedIps,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // --- SPECTACULAR EMOJI RAIN CANVAS RUNNER ---
        fallingList.forEach { p ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = p.initialX * screenWidth.toPx()
                        translationY = p.yPercent * size.height
                        scaleX = 1.3f
                        scaleY = 1.3f
                        rotationZ = p.rotation
                    }
            ) {
                Text(p.emoji, fontSize = 28.sp)
            }
        }

        // --- DETAILED EXPANSION DRAWER/MODAL SHEET FOR COMMENTS ---
        if (selectedSecret != null) {
            val secret = selectedSecret!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { viewModel.selectSecret(null) } // dismiss
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false, onClick = {}) // stop propagation
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(1.dp, CosmicBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    color = CosmicSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                    ) {
                        // Sheet pull bar
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sheet Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = secret.title,
                                    color = TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Category: ${secret.category}",
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(onClick = { viewModel.selectSecret(null) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Detail Tab", tint = TextWhite)
                            }
                        }

                        Divider(color = CosmicBorder, modifier = Modifier.padding(vertical = 8.dp))

                        // Unified inside Scroll listing secret description + comment records
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                // Full expanded Secret Body Card in comments modal
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CosmicSurfaceVariant)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = secret.text,
                                        color = TextWhite,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                                
                                // Show interactive mood meter live analytics
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("AURA INTEGRATION REPORT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = TextGray, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                AuraMoodMeter(secret = secret)
                            }

                            item {
                                Text(
                                    text = "COMMENTS & ECHOES (${comments.size})",
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (comments.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No echoes yet. Whisper a response below.", color = TextMuted, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                // Group replies by parentId
                                val parents = comments.filter { it.parentId == null }
                                val children = comments.filter { it.parentId != null }.groupBy { it.parentId!! }

                                items(parents) { parent ->
                                    CommentThreadNode(
                                        comment = parent,
                                        replies = children[parent.id] ?: emptyList(),
                                        viewModel = viewModel,
                                        onReplySelected = { id ->
                                            replyToId = id
                                        },
                                        onTriggerParticle = { emoji ->
                                            val randX = (40..60).random().toFloat()
                                            floatingList = floatingList + FloatingEmoji(emoji = emoji, initialXHex = randX)
                                        }
                                    )
                                }
                            }
                        }

                        // Bottom Comment Composer row
                        AnimatedVisibility(visible = replyToId != null) {
                            val targetName = comments.find { it.id == replyToId }?.authorName ?: "Anonymous"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicSurfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Replying to $targetName",
                                    fontSize = 11.sp,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Cancel",
                                    fontSize = 11.sp,
                                    color = NeonPink,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { replyToId = null }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicSurfaceVariant)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = commentTextState,
                                onValueChange = { commentTextState = it },
                                placeholder = { Text("Tape a respectful whisper...", color = TextMuted, fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = CosmicSurface,
                                    unfocusedContainerColor = CosmicSurface,
                                    disabledContainerColor = CosmicSurface,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                maxLines = 3,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (commentTextState.trim().isNotEmpty()) {
                                        viewModel.submitNewComment(
                                            secretId = secret.id,
                                            parentId = replyToId,
                                            text = commentTextState
                                        )
                                        commentTextState = ""
                                        replyToId = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("Echo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            }
                        }
                    }
                }
            }
        }

        // --- DYNAMIC WATERFALLING FLOATING REACTIONS PARTICLES ---
        floatingList.forEach { f ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (f.initialXHex / 100f) * screenWidth.toPx() + f.xOffset
                        translationY = screenWidth.toPx() * 1.5f + f.yOffset
                        alpha = f.alpha
                        scaleX = f.scale * 1.5f
                        scaleY = f.scale * 1.5f
                    }
            ) {
                Text(f.emoji, fontSize = 24.sp)
            }
        }

        // --- SUBMIT BROADCAST FLOATING ACTION ACCELERATOR ---
        if (currentMode != "ADMIN" && selectedSecret == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = NeonPink,
                    contentColor = TextWhite,
                    shape = CircleShape,
                    modifier = Modifier.testTag("broadcast_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Whisper a New Secret")
                }
            }
        }

        // --- CREATION FORM MODAL SHEET LAYER ---
        if (showCreateDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { showCreateDialog = false }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false, onClick = {}) // prevent dismiss
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(1.dp, Brush.linearGradient(listOf(NeonPink, ElectricPurple)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    color = CosmicSurface
                ) {
                    var confessionsBody by remember { mutableStateOf("") }
                    var userTitleValue by remember { mutableStateOf("") }
                    var chosenCategory by remember { mutableStateOf("Auto") }
                    val categories = listOf("Auto", "Love", "Guilt", "Regret", "Mystery", "Work", "Fear", "Hope")

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                                .align(Alignment.CenterHorizontally)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "BROADCAST SECRET AURA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonPink,
                                letterSpacing = 2.sp
                            )
                            IconButton(onClick = { showCreateDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Category Pills inside submission
                        Text(
                            "ATMOSPHERIC COLOR VIBE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val active = chosenCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) ElectricPurple else CosmicSurfaceVariant)
                                        .clickable { chosenCategory = cat }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Title selection
                        OutlinedTextField(
                            value = userTitleValue,
                            onValueChange = { userTitleValue = it },
                            label = { Text("Title (Optional - AI generates if empty)", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("secret_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = ElectricPurple,
                                unfocusedBorderColor = CosmicBorder,
                                focusedContainerColor = CosmicSurfaceVariant,
                                unfocusedContainerColor = CosmicSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )

                        // Submission details writing field
                        OutlinedTextField(
                            value = confessionsBody,
                            onValueChange = { confessionsBody = it },
                            placeholder = {
                                Text(
                                    "Speak your mind absolute. Your secrets are encrypted... No names, no logins, no tracks. Live freely.",
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("secret_body_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CosmicBorder,
                                focusedContainerColor = CosmicSurfaceVariant,
                                unfocusedContainerColor = CosmicSurfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )

                        // Safety indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Information Box", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "WhisperAI moderates and filters scams, slurs, threats and explicit links instantly.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        // Submit confirmation
                        Button(
                            onClick = {
                                if (confessionsBody.trim().length > 10) {
                                    viewModel.submitNewSecret(
                                        text = confessionsBody,
                                        title = userTitleValue,
                                        category = chosenCategory
                                    )
                                    showCreateDialog = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_secret_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                            shape = RoundedCornerShape(16.dp),
                            enabled = confessionsBody.trim().length > 10
                        ) {
                            if (isPosting) {
                                CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp))
                            } else {
                                Text("BROADCAST ANONYMOUS VIBE", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextWhite)
                            }
                        }
                    }
                }
            }
        }

        // --- GLOBAL FLOATING NOTIFICATION BANNER ---
        AnimatedVisibility(
            visible = alertMsg != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
        ) {
            alertMsg?.let { text ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CosmicSurfaceVariant.copy(alpha = 0.95f))
                        .border(1.dp, NeonPink, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 18.sp)
                        Text(
                            text = text,
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.dismissAlert() }
                        )
                    }
                }
            }
        }
    }
}

// --- CONFESSION SUB-COMPONENTS & FEED CARDS ---

@Composable
fun SecretConfessionCard(
    secret: SecretEntity,
    viewModel: SecretViewModel,
    isPinnedStyling: Boolean,
    userReactions: List<UserReactionEntity>,
    isBookmarked: Boolean,
    onTriggerFloatingParticle: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isReactionSelectorVisible by remember { mutableStateOf(false) }

    // Dynamic coloring based on Category to form glowing aura
    val categoryGlow = getCategoryColor(secret.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (isPinnedStyling) CosmicSurfaceVariant else CosmicSurface)
            .border(
                width = if (isPinnedStyling) 2.dp else 1.dp,
                color = if (isPinnedStyling) AmberGold else CosmicBorder,
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(secret.id) {
                // Interactive trigger menu options
                detectDragGestures(
                    onDragStart = { isReactionSelectorVisible = true },
                    onDrag = { _, _ -> },
                    onDragEnd = { /* auto dismiss is fine */ }
                )
            }
            .clickable {
                viewModel.selectSecret(secret)
            }
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: Category, Date and Pinned Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(categoryGlow)
                    )
                    Text(
                        text = secret.category,
                        color = categoryGlow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "• " + getRelativeTimeString(secret.timestamp),
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (secret.riskScore >= 0.75) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentRed.copy(alpha = 0.15f))
                                .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("RISK ALERT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        }
                    } else {
                        // AI Verified Safe Badge - Editorial style
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmicBackground)
                                .border(1.dp, CosmicBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan)
                            )
                            Text(
                                text = "AI VERIFIED SAFE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = CyberCyan,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    // Bookmark trigger
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.Star,
                        contentDescription = "Bookmark confession",
                        tint = if (isBookmarked) NeonPink else TextMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { viewModel.toggleBookmark(secret.id) }
                    )
                }
            }

            // Sensational Title
            Text(
                text = secret.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                lineHeight = 22.sp,
                style = LocalTextStyle.current.copy(
                    shadow = Shadow(color = categoryGlow.copy(alpha = 0.2f), blurRadius = 8f)
                )
            )

            // Coded formatted Description
            Text(
                text = "“${secret.text}”",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = TextWhite,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )

            // Quick display of mood levels (Aura strip)
            AuraMoodIndicatorStrip(secret = secret, tintColor = categoryGlow)

            // Bottom interactivity panel: likes, views count, reactions trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View telemetry counters
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👀", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("${secret.views} views", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(TextMuted)
                    )

                    Row(
                        modifier = Modifier.clickable { viewModel.selectSecret(secret) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Discuss", color = CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Interactive Quick Reaction triggers
                val activeReact = userReactions.find { it.targetId == secret.id && it.targetType == "SECRET" }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickEmotions = listOf(
                        "love" to "❤️",
                        "sad" to "😭",
                        "fire" to "🔥",
                        "funny" to "😂"
                    )

                    quickEmotions.forEach { (type, symbol) ->
                        val hasRatedThis = activeReact?.reactionType == type
                        val count = getCountForReaction(secret, type)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (hasRatedThis) categoryGlow.copy(alpha = 0.2f) else CosmicSurfaceVariant)
                                .clickable {
                                    viewModel.reactToSecret(secret.id, type)
                                    onTriggerFloatingParticle(symbol)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$symbol $count",
                                fontSize = 10.sp,
                                color = if (hasRatedThis) TextWhite else TextGray,
                                fontWeight = if (hasRatedThis) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Press-to-pick overlay trigger
                    IconButton(
                        onClick = { isReactionSelectorVisible = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("➕", fontSize = 11.sp)
                    }
                }
            }
        }

        // Expanded absolute floating emoji reaction selector overlay
        AnimatedVisibility(
            visible = isReactionSelectorVisible,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .wrapContentSize()
                    .border(1.dp, CosmicBorder, RoundedCornerShape(24.dp)),
                color = CosmicSurfaceVariant,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val reactionsSet = listOf(
                        "love" to "❤️", "sad" to "😭", "dead" to "💀", "fire" to "🔥",
                        "shocked" to "😳", "angry" to "😤", "watching" to "👀", "funny" to "😂"
                    )
                    reactionsSet.forEach { (type, sym) ->
                        Text(
                            text = sym,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clickable {
                                    viewModel.reactToSecret(secret.id, type)
                                    onTriggerFloatingParticle(sym)
                                    isReactionSelectorVisible = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- COMPOSE RENDERING CANVAS DRAIN-EFFECT LINES ---

@Composable
fun AuraMoodIndicatorStrip(secret: SecretEntity, tintColor: Color) {
    // Dynamic mood calculation based on stored values
    val loves = secret.reactLove.toFloat().coerceAtLeast(0f)
    val sads = secret.reactSad.toFloat().coerceAtLeast(0f)
    val fires = secret.reactFire.toFloat().coerceAtLeast(0f)
    val remaining = (secret.reactFunny + secret.reactShocked + secret.reactSupport + secret.reactWatching + 3).toFloat()

    val sum = loves + sads + fires + remaining
    val loveW = if (sum > 0) loves / sum else 0.25f
    val sadW = if (sum > 0) sads / sum else 0.25f
    val fireW = if (sum > 0) fires / sum else 0.25f
    val remW = 1.0f - loveW - sadW - fireW

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(CosmicSurfaceVariant)
    ) {
        if (loveW > 0.01f) Box(modifier = Modifier.weight(loveW).fillMaxHeight().background(NeonPink))
        if (sadW > 0.01f) Box(modifier = Modifier.weight(sadW).fillMaxHeight().background(AccentBlue))
        if (fireW > 0.01f) Box(modifier = Modifier.weight(fireW).fillMaxHeight().background(AmberGold))
        if (remW > 0.01f) Box(modifier = Modifier.weight(remW).fillMaxHeight().background(CyberCyan))
    }
}

@Composable
fun AuraMoodMeter(secret: SecretEntity) {
    val total = (secret.reactLove + secret.reactSad + secret.reactDead + secret.reactFire +
            secret.reactShocked + secret.reactAngry + secret.reactWatching + secret.reactMindBlown +
            secret.reactFunny + secret.reactCold + secret.reactSupport + secret.reactRedFlag + 12).toFloat()

    val moods = listOf(
        Triple("❤️ Love", secret.reactLove + 3, NeonPink),
        Triple("😭 Grief/Sadness", secret.reactSad + 3, AccentBlue),
        Triple("🔥 Passion/Fire", secret.reactFire + 2, AmberGold),
        Triple("💀 Comedy/Dead", secret.reactFunny + 2, CyberCyan),
        Triple("🫶 Echo Support", secret.reactSupport + 2, AccentGreen)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CosmicSurface)
            .border(1.dp, CosmicBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High fidelity editorial header row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "LIVE AURA ANALYSIS",
                color = TextGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Text(
                "AI Confidence: 98%",
                color = NeonPink,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        moods.forEach { (label, count, color) ->
            val percentage = ((count / total) * 100).toInt().coerceIn(1..100)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$percentage%", color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }

                // Meter Bar with nice modern aesthetics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(CosmicBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentage / 100f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

// --- COMMENTS THREAD NODES COMPOSABLE ---

@Composable
fun CommentReactionsRow(
    comment: CommentEntity,
    viewModel: SecretViewModel,
    onTriggerParticle: (String) -> Unit
) {
    var isTrayOpen by remember { mutableStateOf(false) }

    val reactionsList = listOf(
        "love" to "❤️", "sad" to "😭", "dead" to "💀", "fire" to "🔥",
        "shocked" to "😳", "angry" to "😤", "watching" to "👀", "mind_blown" to "🤯",
        "funny" to "😂", "cold" to "🥶", "support" to "🫶", "red_flag" to "🚩"
    )

    val activeReactions = listOf(
        "love" to comment.reactLove,
        "sad" to comment.reactSad,
        "dead" to comment.reactDead,
        "fire" to comment.reactFire,
        "shocked" to comment.reactShocked,
        "angry" to comment.reactAngry,
        "watching" to comment.reactWatching,
        "mind_blown" to comment.reactMindBlown,
        "funny" to comment.reactFunny,
        "cold" to comment.reactCold,
        "support" to comment.reactSupport,
        "red_flag" to comment.reactRedFlag
    ).filter { it.second > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            // Display already activated pills
            activeReactions.forEach { (type, count) ->
                val emoji = getEmojiForReactionType(type)
                val color = when (type) {
                    "love" -> NeonPink
                    "sad" -> AccentBlue
                    "dead" -> CyberCyan
                    "fire" -> AmberGold
                    "support" -> AccentGreen
                    "angry" -> Color(0xFFE57373)
                    else -> TextGray
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.reactToComment(comment.id, type)
                            onTriggerParticle(emoji)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$emoji $count",
                        fontSize = 10.sp,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Toggle Tray Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CosmicSurface)
                    .border(1.dp, CosmicBorder, CircleShape)
                    .clickable { isTrayOpen = !isTrayOpen },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTrayOpen) "✕" else "+",
                    fontSize = 10.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick Report flag icon button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        viewModel.submitReport(comment.id, "COMMENT", "Abusive or toxic language")
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Report",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }

        // Expanded Horizontal Reaction Selection Panel
        AnimatedVisibility(
            visible = isTrayOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmicBackground)
                    .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                reactionsList.forEach { (type, emoji) ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                viewModel.reactToComment(comment.id, type)
                                onTriggerParticle(emoji)
                                isTrayOpen = false
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentThreadNode(
    comment: CommentEntity,
    replies: List<CommentEntity>,
    viewModel: SecretViewModel,
    onReplySelected: (Long) -> Unit,
    onTriggerParticle: (String) -> Unit
) {
    var isThreadCollapsed by remember { mutableStateOf(comment.isToxic) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Parent Comment Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar Circle representation
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(comment.authorColorHex)))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.authorName.firstOrNull()?.toString() ?: "A",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            // Text Body block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.authorName,
                        fontWeight = FontWeight.Bold,
                        color = Color(android.graphics.Color.parseColor(comment.authorColorHex)),
                        fontSize = 11.sp
                    )
                    Text(
                        text = getRelativeTimeString(comment.timestamp),
                        color = TextMuted,
                        fontSize = 8.sp
                    )
                }

                if (comment.isToxic && isThreadCollapsed) {
                    // Safe filter
                    Text(
                        text = "[WhisperAI collapsed this comment due to potential hostility. Click to show]",
                        color = NeonPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { isThreadCollapsed = false }
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = comment.text,
                        color = TextWhite,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Serif
                    )

                    // Modern Comment reactions row
                    CommentReactionsRow(comment = comment, viewModel = viewModel, onTriggerParticle = onTriggerParticle)

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Reply",
                        fontSize = 10.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onReplySelected(comment.id) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Child Nested comments sub-nodes with custom vertical timeline lines
        if (replies.isNotEmpty() && (!comment.isToxic || !isThreadCollapsed)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Indigo indentation trace
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 20.dp)
                        .background(CosmicBorder)
                )
                Spacer(modifier = Modifier.width(16.dp))

                // Recursive sub listing
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    replies.forEach { child ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(child.authorColorHex))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = child.authorName.firstOrNull()?.toString() ?: "A",
                                    color = TextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = child.authorName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(android.graphics.Color.parseColor(child.authorColorHex)),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = getRelativeTimeString(child.timestamp),
                                        color = TextMuted,
                                        fontSize = 7.sp
                                    )
                                }
                                Text(
                                    text = child.text,
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Serif
                                )
                                // Active Reactions Row for Child Comments
                                CommentReactionsRow(comment = child, viewModel = viewModel, onTriggerParticle = onTriggerParticle)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- IMMERSIVE SWIPE TIKTOK-STYLE DECK ---

@Composable
fun TikTokSwipeDeck(
    secrets: List<SecretEntity>,
    viewModel: SecretViewModel,
    userReactions: List<UserReactionEntity>,
    bookmarkedIds: Set<Long>,
    onTriggerFloatingParticle: (String) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val secret = secrets.getOrNull(currentIndex) ?: return
    
    // Swipe gestures
    var dragAmountY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(secrets.size) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAmountY += dragAmount.y
                    },
                    onDragEnd = {
                        if (dragAmountY < -150f) {
                            // Swipe UP -> Next post
                            if (currentIndex < secrets.size - 1) {
                                currentIndex++
                                viewModel.incrementViews(secrets[currentIndex].id)
                            } else {
                                currentIndex = 0
                            }
                        } else if (dragAmountY > 150f) {
                            // Swipe DOWN -> Prev post
                            if (currentIndex > 0) {
                                currentIndex--
                                viewModel.incrementViews(secrets[currentIndex].id)
                            } else {
                                currentIndex = secrets.size - 1
                            }
                        }
                        dragAmountY = 0f
                    }
                )
            }
    ) {
        // High fidelity immersive card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CosmicSurfaceVariant)
                .border(2.dp, getCategoryColor(secret.category), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Card Top details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(getCategoryColor(secret.category)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(secret.category.uppercase(), color = getCategoryColor(secret.category), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (bookmarkedIds.contains(secret.id)) Icons.Default.Favorite else Icons.Default.Star,
                            tint = if (bookmarkedIds.contains(secret.id)) NeonPink else TextWhite,
                            contentDescription = "Bookmark",
                            modifier = Modifier.clickable { viewModel.toggleBookmark(secret.id) }
                        )
                    }
                }

                // Immersive full quote block central
                Column {
                    Text(
                        text = "“",
                        fontSize = 62.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = getCategoryColor(secret.category).copy(alpha = 0.3f),
                        lineHeight = 0.sp
                    )
                    Text(
                        text = secret.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 32.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = secret.text,
                        fontSize = 16.sp,
                        color = TextWhite,
                        lineHeight = 24.sp,
                        fontFamily = FontFamily.Serif
                    )
                }

                // Immersive card dynamic bottom telemetry stats
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuraMoodMeter(secret = secret)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Swipe up or down to navigate",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )

                        // Quick love react trigger
                        Button(
                            onClick = {
                                viewModel.reactToSecret(secret.id, "love")
                                onTriggerFloatingParticle("❤️")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                            shape = CircleShape
                        ) {
                            Text("❤️ React Support", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }
    }
}

// --- POLLS AND COMMUNITY BATTLES CONSOLE PANEL ---

@Composable
fun ConfessionBattlesPanel(
    pollAApproved: MutableState<Int>,
    pollBApproved: MutableState<Int>,
    userPolledOption: MutableState<String?>,
    onVote: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "COMMUNITY MYSTERY BATTLES",
            color = CyberCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "Anonymous, real-time value debates. Vote to reveal the collective community consciousness index details.",
            color = TextGray,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Battle Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CosmicSurface)
                .border(2.dp, ElectricPurple, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AccentRed))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HOT VALUE DILEMMA", color = AccentRed, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Text(
                    text = "My partner of 5 years still keeps physical letters from their high school first crush hidden in an old shoe box. Is it okay to sneak read them?",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                // Option Voting
                val total = (pollAApproved.value + pollBApproved.value + 150).toFloat()
                val percentageA = (((pollAApproved.value + 45) / total) * 100).toInt()
                val percentageB = 100 - percentageA

                // Option A details button
                val rated = userPolledOption.value != null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (userPolledOption.value == "A") NeonPink.copy(alpha = 0.2f) else CosmicSurfaceVariant)
                        .border(1.dp, if (userPolledOption.value == "A") NeonPink else CosmicBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            if (!rated) {
                                pollAApproved.value++
                                userPolledOption.value = "A"
                                onVote("A")
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("No, respect their complete privacy.", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (rated) {
                            Text("$percentageA%", color = NeonPink, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Option B details button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (userPolledOption.value == "B") CyberCyan.copy(alpha = 0.2f) else CosmicSurfaceVariant)
                        .border(1.dp, if (userPolledOption.value == "B") CyberCyan else CosmicBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            if (!rated) {
                                pollBApproved.value++
                                userPolledOption.value = "B"
                                onVote("B")
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Yes, secrets destroy partnerships eventually.", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (rated) {
                            Text("$percentageB%", color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminModerationConsole(
    allSecretsAndFlagged: List<SecretEntity>,
    bannedIps: List<SpammerBanEntity>,
    viewModel: SecretViewModel
) {
    var isAdminAuthenticated by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }
    var diagnosticLogIndex by remember { mutableStateOf(0) }
    val simulatedLogs = remember {
        listOf(
            "ESTABLISHING SECURE PROTOCOL GATEWAY...",
            "INITIALIZING DEEPMIND AI HARBINGER RESILIENCE...",
            "SUCCESS: FIREWALL DECRYPT KEYS GENERATED.",
            "WARNING: SYSTEM UNDER OFF-GRID TELEMETRY SHIELD.",
            "FETCHING CLOUD SPANNER COMPLIANCE REGISTRY...",
            "ALERT: 1 PENDING FLAG DETECTED WITH ANOMALY SCORE.",
            "AURA PLATFORM DECRYPTION SUCCESSFUL."
        )
    }

    LaunchedEffect(isAdminAuthenticated) {
        if (!isAdminAuthenticated) {
            while (diagnosticLogIndex < simulatedLogs.size - 1) {
                delay(1200)
                diagnosticLogIndex++
            }
        }
    }

    if (!isAdminAuthenticated) {
        // Holographic secure passcode gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CosmicSurface)
                    .border(1.dp, CosmicBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🛡️ SECURE COGNITIVE GATE",
                    color = NeonPink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "AUTHORIZATION MATRIX REQUIRED • CHIP V4.91",
                    color = TextGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                // PIN Asterisks
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val active = pinInput.length > i
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (active) CyberCyan else CosmicSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (active) CyberCyan else CosmicBorder,
                                    CircleShape
                                )
                        )
                    }
                }

                if (authError) {
                    Text(
                        text = "ACCESS DENIED • CORES REPORT SUSPICIOUS SIGNALS",
                        color = AccentRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Numeric keypad grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CLEAR", "0", "ENTER")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { digit ->
                                val isWide = digit == "CLEAR" || digit == "ENTER"
                                Box(
                                    modifier = Modifier
                                        .weight(if (isWide) 1.2f else 1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CosmicSurfaceVariant)
                                        .border(1.dp, CosmicBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            authError = false
                                            when (digit) {
                                                "CLEAR" -> if (pinInput.isNotEmpty()) pinInput = ""
                                                "ENTER" -> {
                                                    if (pinInput == "7777") {
                                                        isAdminAuthenticated = true
                                                    } else {
                                                        authError = true
                                                        pinInput = ""
                                                    }
                                                }
                                                else -> {
                                                    if (pinInput.length < 4) pinInput += digit
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = digit,
                                        color = if (digit == "ENTER") AccentGreen else if (digit == "CLEAR") AccentRed else TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isWide) 10.sp else 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Scrolling SSH Diagnostics Terminal Console
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "TERMINAL GATE ACTIVE PROTOCOLS:",
                        color = Color(0xFF64DD17),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    for (i in 0..diagnosticLogIndex) {
                        Text(
                            text = simulatedLogs.getOrNull(i) ?: "",
                            color = Color(0xFFAEEA00),
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Text(
                    text = "🔒 Simulated Clearance Code Hint: 7777",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    } else {
        // Unlocked Futuristic Dashboard UI Control Center
        var activeSubTab by remember { mutableStateOf("QUEUE") } // QUEUE, REPORTS, ANALYTICS, BLACKLIST
        var queueTypeSelector by remember { mutableStateOf("SECRETS") } // SECRETS, COMMENTS

        val commentsList by viewModel.allCommentsList.collectAsStateWithLifecycle()
        val reportsList by viewModel.allReports.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🛡️ GLOBAL THREAT INTELLIGENCE",
                        color = CyberCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "SECURED TERMINAL • ACTIVE SESSION",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentRed.copy(alpha = 0.15f))
                        .clickable { isAdminAuthenticated = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("LOCK GATE", fontSize = 8.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                }
            }

            // Tabs Panel selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmicSurface)
                    .padding(3.dp)
            ) {
                val subTabs = listOf(
                    "QUEUE" to "Audit",
                    "REPORTS" to "Reports",
                    "ANALYTICS" to "Telemetry",
                    "BLACKLIST" to "Bans"
                )
                subTabs.forEach { (modeCode, label) ->
                    val active = activeSubTab == modeCode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) CosmicSurfaceVariant else Color.Transparent)
                            .clickable { activeSubTab = modeCode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (active) CyberCyan else TextGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Main display depending on active tab
            Box(modifier = Modifier.weight(1f)) {
                when (activeSubTab) {
                    "QUEUE" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Secrets or Comments Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicSurfaceVariant)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (queueTypeSelector == "SECRETS") NeonPink.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable { queueTypeSelector = "SECRETS" }
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Secrets Queue", color = TextWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (queueTypeSelector == "COMMENTS") ElectricPurple.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable { queueTypeSelector = "COMMENTS" }
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Comments Queue", color = TextWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (queueTypeSelector == "SECRETS") {
                                val pendingList = allSecretsAndFlagged.filter { it.moderationStatus == "PENDING" || it.moderationStatus == "FLAGGED" }
                                if (pendingList.isEmpty()) {
                                    EmptyStatePlaceholder("All secrets cleared! Safety queue is clean.")
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                        items(pendingList) { secret ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .border(1.dp, if (secret.moderationStatus == "FLAGGED") AccentRed else CosmicBorder, RoundedCornerShape(14.dp)),
                                                color = CosmicSurface
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "SECRET ID: #${secret.id} • IP: ${secret.userIpSimulated}",
                                                            fontSize = 8.sp,
                                                            color = TextMuted,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            secret.moderationStatus,
                                                            color = if (secret.moderationStatus == "FLAGGED") AccentRed else AmberGold,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }

                                                    Text(secret.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                                    Text(secret.text, fontSize = 11.sp, color = TextGray)

                                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                        Text("AI toxicity index: ${String.format("%.2f", secret.riskScore)}", fontSize = 8.sp, color = NeonPink, fontWeight = FontWeight.Bold)
                                                        Text("AI accuracy: ${String.format("%.1f", secret.confidenceScore * 100)}%", fontSize = 8.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.adminApproveSecret(secret.id) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Text("APPROVE", color = CosmicBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = { viewModel.adminRejectSecret(secret.id) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Text("FLAG SAFETY", color = CosmicBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        IconButton(
                                                            onClick = { viewModel.adminDeleteSecret(secret.id) },
                                                            modifier = Modifier
                                                                .background(AccentRed, RoundedCornerShape(8.dp))
                                                                .size(36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextWhite, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Comments Queue
                                if (commentsList.isEmpty()) {
                                    EmptyStatePlaceholder("Comments database empty.")
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                        items(commentsList) { comment ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .border(1.dp, if (comment.isToxic) AccentRed else CosmicBorder, RoundedCornerShape(14.dp)),
                                                color = CosmicSurface
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            "COMMENT ID: #${comment.id} • AUTHOR: ${comment.authorName}",
                                                            fontSize = 8.sp,
                                                            color = TextMuted,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        Text(
                                                            if (comment.isToxic) "AUTO-TOXIC" else "CLEAN",
                                                            color = if (comment.isToxic) AccentRed else AccentGreen,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }

                                                    Text(comment.text, fontSize = 11.sp, color = TextWhite)

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.submitReport(comment.id, "COMMENT", "Admin verified inappropriate behavior") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.weight(1f),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Text("FLAG TICKET", color = CosmicBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        IconButton(
                                                            onClick = { viewModel.adminDeleteComment(comment.id) },
                                                            modifier = Modifier
                                                                .background(AccentRed, RoundedCornerShape(8.dp))
                                                                .size(36.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextWhite, modifier = Modifier.size(16.dp))
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

                    "REPORTS" -> {
                        // Secure User safety ticket flow
                        if (reportsList.isEmpty()) {
                            EmptyStatePlaceholder("No user tickets registered! Core systems fully obedient.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(reportsList) { ticket ->
                                    val resolved = ticket.isResolved
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, if (resolved) AccentGreen.copy(alpha = 0.5f) else AccentRed, RoundedCornerShape(14.dp)),
                                        color = CosmicSurface
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "TICKET ID: #${ticket.id} • TYPE: ${ticket.targetType}",
                                                    fontSize = 8.sp,
                                                    color = TextMuted,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = if (resolved) "RESOLVED" else "ACTION REQUIRED",
                                                    color = if (resolved) AccentGreen else AccentRed,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            Text(
                                                text = "Reason Reported: ${ticket.reason}",
                                                fontSize = 11.sp,
                                                color = TextWhite,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                Text("Reporter IP: ${ticket.reporterIp}", fontSize = 8.sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                                Text("Target Ref ID: ${ticket.targetId}", fontSize = 8.sp, color = AmberGold, fontFamily = FontFamily.Monospace)
                                            }

                                            if (!resolved) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Resolve ticket
                                                    Button(
                                                        onClick = { viewModel.adminResolveReport(ticket.id) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("RESOLVE TICKET", color = CosmicBackground, fontSize = 8.sp)
                                                    }

                                                    // Execute content deletion directly
                                                    Button(
                                                        onClick = {
                                                            if (ticket.targetType == "SECRET") {
                                                                viewModel.adminDeleteSecret(ticket.targetId)
                                                            } else {
                                                                viewModel.adminDeleteComment(ticket.targetId)
                                                            }
                                                            viewModel.adminResolveReport(ticket.id)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1.5f),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("DELETE TARGET", color = TextWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Ban simulated network reporter
                                                    Button(
                                                        onClick = {
                                                            viewModel.adminBanIp(ticket.reporterIp, "Spammer lodging faulty reports.")
                                                            viewModel.adminResolveReport(ticket.id)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("BAN REPORTER", color = TextWhite, fontSize = 8.sp)
                                                    }
                                                }
                                            } else {
                                                // Option to delete log
                                                Button(
                                                    onClick = { viewModel.adminDeleteReport(ticket.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceVariant),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("PURGE HISTORIC RESOLVED TICKET LOG", color = TextMuted, fontSize = 8.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "ANALYTICS" -> {
                        // AI Telemetry analytics with dynamic grids and curves custom-drawn in Canvas!
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("AI TOXICITY INDEX REAL-TIME TRACKER", fontSize = 10.sp, color = TextWhite, fontWeight = FontWeight.Bold)

                            // Dynamic Canvas curve graph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CosmicSurface)
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Draw background lines
                                    val lines = 3
                                    val intervalY = size.height / lines
                                    for (i in 0..lines) {
                                        drawLine(
                                            color = CosmicBorder.copy(alpha = 0.6f),
                                            start = Offset(0f, i * intervalY),
                                            end = Offset(size.width, i * intervalY),
                                            strokeWidth = 1f
                                        )
                                    }

                                    // Dynamic points simulation
                                    val points = listOf(
                                        Offset(0f, size.height * 0.75f),
                                        Offset(size.width * 0.15f, size.height * 0.55f),
                                        Offset(size.width * 0.3f, size.height * 0.85f),
                                        Offset(size.width * 0.45f, size.height * 0.32f),
                                        Offset(size.width * 0.6f, size.height * 0.7f),
                                        Offset(size.width * 0.75f, size.height * 0.15f),
                                        Offset(size.width * 0.9f, size.height * 0.8f),
                                        Offset(size.width, size.height * 0.45f)
                                    )

                                    val path = androidx.compose.ui.graphics.Path()
                                    path.moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) {
                                        val p0 = points[i - 1]
                                        val p1 = points[i]
                                        path.quadraticBezierTo(
                                            (p0.x + p1.x) / 2f, p0.y,
                                            p1.x, p1.y
                                        )
                                    }

                                    drawPath(
                                        path = path,
                                        color = NeonPink,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 6f, 
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    )

                                    // Glowing underneath gradient
                                    val fillPath = androidx.compose.ui.graphics.Path().apply {
                                        addPath(path)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            listOf(NeonPink.copy(alpha = 0.2f), Color.Transparent)
                                        )
                                    )

                                    // Nodes
                                    points.forEach { pt ->
                                        drawCircle(
                                            color = CyberCyan,
                                            radius = 8f,
                                            center = pt
                                        )
                                    }
                                }
                            }

                            // Real time platform stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                val summaryMetrics = listOf(
                                    "🧪 TOXIC VECTOR: 1.45%",
                                    "🛡️ AI ACCURACY: 99.1%",
                                    "📊 TOTAL AUDITED: ${allSecretsAndFlagged.size}"
                                )
                                summaryMetrics.forEach {
                                    Text(it, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextGray, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("WHISPER CONVERSATION DENSITY MAP", fontSize = 10.sp, color = TextWhite, fontWeight = FontWeight.Bold)

                            // Heatmap Grid drawn with canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CosmicSurface)
                                    .border(1.dp, CosmicBorder, RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cols = 12
                                    val rows = 4
                                    val cellWidth = size.width / cols
                                    val cellHeight = size.height / rows
                                    for (r in 0 until rows) {
                                        for (c in 0 until cols) {
                                            // Seed random mock density
                                            val seed = (r * c * 31) % 100
                                            val color = when {
                                                seed > 80 -> NeonPink
                                                seed > 55 -> ElectricPurple
                                                seed > 30 -> CyberCyan
                                                seed > 10 -> CosmicBorder
                                                else -> CosmicSurfaceVariant.copy(alpha = 0.3f)
                                            }
                                            drawRoundRect(
                                                color = color,
                                                topLeft = Offset(c * cellWidth + 3f, r * cellHeight + 3f),
                                                size = Size(cellWidth - 6f, cellHeight - 6f),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Heatmap guide
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("LESS", fontSize = 7.sp, color = TextMuted)
                                Spacer(modifier = Modifier.width(4.dp))
                                val levels = listOf(CosmicBorder, CyberCyan, ElectricPurple, NeonPink)
                                levels.forEach { lvl ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(lvl)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text("MORE", fontSize = 7.sp, color = TextMuted)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action Log audit alerts
                            Text("COGNITIVE FIREWALL CONSOLE ALERTS", fontSize = 10.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = CosmicSurface
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val logs = listOf(
                                        "[ALERT CODE] System collapsed reported responses automatically.",
                                        "[ALERT CODE] IP restrictor is fully active and defending spam entries.",
                                        "[ALERT CODE] Threading analyzer report: safety factor is within margins."
                                    )
                                    logs.forEach { log ->
                                        Text(log, fontSize = 8.sp, color = AccentRed, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }

                    "BLACKLIST" -> {
                        // Banning panel
                        var inputIpToBan by remember { mutableStateOf("") }
                        var ipBanReason by remember { mutableStateOf("Spam activity or toxic commentary") }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = inputIpToBan,
                                    onValueChange = { inputIpToBan = it },
                                    placeholder = { Text("IP (e.g. 192.168.0.12)", color = TextMuted, fontSize = 11.sp) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(52.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        focusedBorderColor = ElectricPurple,
                                        unfocusedBorderColor = CosmicBorder
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Button(
                                    onClick = {
                                        if (inputIpToBan.trim().isNotEmpty()) {
                                            viewModel.adminBanIp(inputIpToBan.trim(), ipBanReason)
                                            inputIpToBan = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .height(52.dp)
                                ) {
                                    Text("BAN IP", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text("SUSPENDED NETWORK IP ADDRESSES", fontSize = 11.sp, color = TextWhite, fontWeight = FontWeight.Bold)

                            if (bannedIps.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No network IPs restricted currently.", color = TextMuted, fontSize = 11.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                                    items(bannedIps) { ban ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(CosmicSurface)
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(ban.simulatedIp, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                Text("Suspension: ${ban.reason}", color = TextGray, fontSize = 9.sp)
                                            }

                                            IconButton(
                                                onClick = { viewModel.adminUnbanIp(ban.simulatedIp) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Pardon ban", tint = AccentRed, modifier = Modifier.size(16.dp))
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

// --- GLOBAL ATOMICAL UI HELPER FUNCTIONS ---

fun getCategoryColor(cat: String): Color {
    return when (cat.lowercase()) {
        "love" -> NeonPink
        "guilt" -> AmberGold
        "regret" -> AccentBlue
        "mystery" -> CyberCyan
        "work" -> ElectricPurple
        "fear" -> Color(0xFFFF6D00)
        "hope" -> AccentGreen
        else -> Color(0xFFE2DDF5)
    }
}

fun getEmojiForReactionType(type: String): String {
    return when (type.lowercase()) {
        "love" -> "❤️"
        "sad" -> "😭"
        "dead" -> "💀"
        "fire" -> "🔥"
        "shocked" -> "😳"
        "angry" -> "😤"
        "watching" -> "👀"
        "mind_blown" -> "🤯"
        "funny" -> "😂"
        "cold" -> "🥶"
        "support" -> "🫶"
        "red_flag" -> "🚩"
        else -> "✨"
    }
}

fun getCountForReaction(s: SecretEntity, type: String): Int {
    return when (type) {
        "love" -> s.reactLove
        "sad" -> s.reactSad
        "dead" -> s.reactDead
        "fire" -> s.reactFire
        "shocked" -> s.reactShocked
        "angry" -> s.reactAngry
        "watching" -> s.reactWatching
        "mind_blown" -> s.reactMindBlown
        "funny" -> s.reactFunny
        "cold" -> s.reactCold
        "support" -> s.reactSupport
        "red_flag" -> s.reactRedFlag
        else -> 0
    }
}

fun getRelativeTimeString(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
         diff < 60000 -> "just now"
         diff < 3600000 -> "${diff / 60000}m ago"
         diff < 86400000 -> "${diff / 3600000}h ago"
         else -> {
             val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
             sdf.format(Date(time))
         }
    }
}

@Composable
fun EmptyStatePlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🤫",
                fontSize = 48.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = 0.5f
                }
            )
            Text(
                text = text,
                color = TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

// Custom Border with multi gradient line support
@Composable
fun DynamicBorderShape() = Brush.sweepGradient(listOf(NeonPink, ElectricPurple, CyberCyan, NeonPink))
