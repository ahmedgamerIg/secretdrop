package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SecretViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("anonymous_secrets_prefs", Context.MODE_PRIVATE)
    private val secretDao = SecretDatabase.getDatabase(application).secretDao()
    private val repository = SecretRepository(secretDao)

    // --- CHANNELS / EVENTS FOR CINEMATIC MOTIONS ---
    private val _emojiRainTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val emojiRainTrigger = _emojiRainTrigger.asSharedFlow()

    private val _alertEvent = MutableStateFlow<String?>(null)
    val alertEvent = _alertEvent.asStateFlow()

    // --- REPOSITORY SOURCE STATE OBSERVATIONS ---
    val approvedSecrets: StateFlow<List<SecretEntity>> = repository.approvedSecrets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSecretsAdmin: StateFlow<List<SecretEntity>> = repository.allSecrets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val secretOfDay: StateFlow<SecretEntity?> = repository.secretOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bannedIps: StateFlow<List<SpammerBanEntity>> = repository.bannedIps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userReactions: StateFlow<List<UserReactionEntity>> = repository.userReactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReports: StateFlow<List<ReportEntity>> = repository.allReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCommentsList: StateFlow<List<CommentEntity>> = repository.allCommentsList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- VIEWMODEL UI STATES ---
    private val _selectedSecret = MutableStateFlow<SecretEntity?>(null)
    val selectedSecret = _selectedSecret.asStateFlow()

    val commentsForSelected: StateFlow<List<CommentEntity>> = _selectedSecret
        .flatMapLatest { secret ->
            if (secret != null) repository.getCommentsForSecret(secret.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPostingInProgress = MutableStateFlow(false)
    val isPostingInProgress = _isPostingInProgress.asStateFlow()

    private val _currentFeedMode = MutableStateFlow("ALL") // ALL, TIKTOK, BATTLES, ADMIN, BOOKMARKS
    val currentFeedMode = _currentFeedMode.asStateFlow()

    // --- BOOKMARKS (Persisted via comma-separated string in Prefs) ---
    private val _bookmarkedIds = MutableStateFlow<Set<Long>>(emptySet())
    val bookmarkedIds = _bookmarkedIds.asStateFlow()

    // --- GAMIFICATION STATS (Persisted locally in Prefs for offline-safe continuous records!) ---
    private val _karmaScore = MutableStateFlow(0)
    val karmaScore = _karmaScore.asStateFlow()

    private val _streakCount = MutableStateFlow(0)
    val streakCount = _streakCount.asStateFlow()

    private val _lastPostTime = MutableStateFlow(0L)

    // Simulated local IP Address for this user (resets on reinstall, but persists during runs)
    val simulatedIpAddress: String

    init {
        // Instantiate beautiful seed secrets if SQLite is empty
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Cache simulated IP for simulated administrator ban testing
        var ip = sharedPrefs.getString("simulated_user_ip", "") ?: ""
        if (ip.isEmpty()) {
            ip = "${(100..254).random()}.${(10..254).random()}.${(10..254).random()}.${(2..254).random()}"
            sharedPrefs.edit().putString("simulated_user_ip", ip).apply()
        }
        simulatedIpAddress = ip

        // Restore karma and streak
        _karmaScore.value = sharedPrefs.getInt("karma_score", 100) // start with 100 karma bonus!
        _streakCount.value = sharedPrefs.getInt("streak_count", 3) // start with 3 days as baseline!
        _lastPostTime.value = sharedPrefs.getLong("last_post_time", 0L)

        // Restore bookmarks
        val bookmarkString = sharedPrefs.getString("bookmark_ids", "") ?: ""
        if (bookmarkString.isNotEmpty()) {
            _bookmarkedIds.value = bookmarkString.split(",")
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        }

        // Check for daily streak degradation (if last post was > 48 hours ago, reset streak!)
        val timeNow = System.currentTimeMillis()
        if (_lastPostTime.value > 0L && (timeNow - _lastPostTime.value) > 172800000L) {
            _streakCount.value = 0
            sharedPrefs.edit().putInt("streak_count", 0).apply()
        }
    }

    // --- USER SUBMISSIONS ---

    fun submitNewSecret(text: String, title: String, category: String) {
        if (text.trim().isEmpty()) return
        
        viewModelScope.launch {
            _isPostingInProgress.value = true
            _alertEvent.value = null
            
            val result = repository.submitSecret(
                text = text,
                userTitle = title,
                category = category,
                ip = simulatedIpAddress
            )

            _isPostingInProgress.value = false
            
            result.onSuccess { savedSecret ->
                // Successful submission rewards karma!
                incrementKarma(25)
                
                // Track post streak activity
                val now = System.currentTimeMillis()
                val diff = now - _lastPostTime.value
                if (diff in 1..86400000) {
                    // Posted twice within the same day - retain streak
                } else if (diff in 86400001..172800000) {
                    // Next consecutive day streak increment!
                    incrementStreak()
                } else {
                    // Reset or rebuild streak
                    _streakCount.value = 1
                    sharedPrefs.edit().putInt("streak_count", 1).apply()
                }
                _lastPostTime.value = now
                sharedPrefs.edit().putLong("last_post_time", now).apply()

                if (savedSecret.moderationStatus == "PENDING") {
                    _alertEvent.value = "Confession sent! Under processing by AI Audit safety review queue first."
                } else if (savedSecret.moderationStatus == "FLAGGED") {
                    _alertEvent.value = "Confession flagged: risk index is high. Sent to admin safety audit."
                } else {
                    _alertEvent.value = "Confession broadcasted successfully! Karma +25."
                    // Rain emojis
                    triggerEmojiRain(savedSecret.category)
                }
            }.onFailure { err ->
                _alertEvent.value = err.message ?: "Submission error."
            }
        }
    }

    fun submitNewComment(secretId: Long, parentId: Long?, text: String) {
        if (text.trim().isEmpty()) return

        viewModelScope.launch {
            // Generate a funny random animal alias
            val animals = listOf("Panda", "Koala", "Fox", "Meerkat", "Raccoon", "Otter", "Phoenix", "Leopard", "Wolf", "Beaver", "Dolphin", "Cheetah")
            val colors = listOf("#FF4081", "#7C4DFF", "#00E5FF", "#FFD600", "#00E676", "#FF6D00", "#E040FB", "#00B0FF")
            
            val randomAlias = "Anonymous ${animals.random()}"
            val randomColor = colors.random()

            val result = repository.submitComment(
                secretId = secretId,
                parentId = parentId,
                authorName = randomAlias,
                authorColorHex = randomColor,
                text = text
            )

            result.onSuccess { comment ->
                incrementKarma(5)
                // Refresh comments view
                val currentSecret = _selectedSecret.value
                if (currentSecret != null && currentSecret.id == secretId) {
                    // Just triggering updates
                }
            }
        }
    }

    // --- REACTION CHOOSERS ---

    fun reactToSecret(secretId: Long, reactionType: String) {
        viewModelScope.launch {
            val success = repository.reactToSecret(secretId, reactionType)
            if (success) {
                incrementKarma(2)
                
                // Refresh active selected secret detailing screen
                val sel = _selectedSecret.value
                if (sel != null && sel.id == secretId) {
                    // Read updated secret to push immediately down UI flow
                    val updated = repository.getSecretFlow(secretId).first()
                    _selectedSecret.value = updated
                }

                // If highly reacted, trigger a spectacular emoji rain
                val updated = repository.getSecretFlow(secretId).first()
                if (updated != null) {
                    val totalReactions = updated.reactLove + updated.reactSad + updated.reactFire + updated.reactFunny + updated.reactShocked
                    if (totalReactions > 50 && (totalReactions % 5 == 0)) {
                        triggerEmojiRain(reactionType)
                    }
                }
            }
        }
    }

    fun reactToComment(commentId: Long, reactionType: String) {
        viewModelScope.launch {
            repository.reactToComment(commentId, reactionType)
        }
    }

    // --- UI NAVIGATION NAVIGATION ACTIONS ---

    fun selectSecret(secret: SecretEntity?) {
        _selectedSecret.value = secret
        if (secret != null) {
            viewModelScope.launch {
                repository.incrementViews(secret.id)
            }
        }
    }

    fun incrementViews(id: Long) {
        viewModelScope.launch {
            repository.incrementViews(id)
        }
    }

    fun changeFeedMode(mode: String) {
        _currentFeedMode.value = mode
    }

    fun selectRandomSecret() {
        viewModelScope.launch {
            val list = approvedSecrets.value
            if (list.isNotEmpty()) {
                val rand = list.random()
                selectSecret(rand)
            }
        }
    }

    // --- BOOKMARK OPERATIONS ---

    fun toggleBookmark(id: Long) {
        val current = _bookmarkedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _bookmarkedIds.value = current
        val joined = current.joinToString(",")
        sharedPrefs.edit().putString("bookmark_ids", joined).apply()
    }

    // --- ADMINISTRATIVE DASHBOARD PANEL ACTIONS (HIDDEN TO THE USER) ---

    fun adminApproveSecret(id: Long) {
        viewModelScope.launch {
            val secret = repository.getSecretFlow(id).first()
            if (secret != null) {
                repository.updateSecretDirect(secret.copy(moderationStatus = "APPROVED"))
            }
        }
    }

    fun adminRejectSecret(id: Long) {
        viewModelScope.launch {
            val secret = repository.getSecretFlow(id).first()
            if (secret != null) {
                repository.updateSecretDirect(secret.copy(moderationStatus = "FLAGGED"))
            }
        }
    }

    fun adminDeleteSecret(id: Long) {
        viewModelScope.launch {
            repository.deleteSecret(id)
            if (_selectedSecret.value?.id == id) {
                _selectedSecret.value = null
            }
        }
    }

    fun adminDeleteComment(commentId: Long) {
        viewModelScope.launch {
            repository.deleteComment(commentId)
        }
    }

    fun adminBanIp(ip: String, reason: String) {
        viewModelScope.launch {
            repository.banIp(ip, reason)
        }
    }

    fun adminUnbanIp(ip: String) {
        viewModelScope.launch {
            repository.unbanIp(ip)
        }
    }

    fun submitReport(targetId: Long, targetType: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetId, targetType, reason, simulatedIpAddress)
            _alertEvent.value = "Flagged content reported successfully for moderation."
        }
    }

    fun adminResolveReport(id: Long) {
        viewModelScope.launch {
            repository.resolveReport(id)
        }
    }

    fun adminDeleteReport(id: Long) {
        viewModelScope.launch {
            repository.deleteReport(id)
        }
    }

    fun toggleSecretOfDay(id: Long) {
        viewModelScope.launch {
            val secret = repository.getSecretFlow(id).first()
            if (secret != null) {
                if (secret.isSecretOfDay) {
                    // Turn off
                    repository.updateSecretDirect(secret.copy(isSecretOfDay = false))
                } else {
                    repository.makeSecretOfDay(id)
                }
            }
        }
    }

    // --- HELPER DYNAMIC STATE MODULATORS ---

    fun triggerEmojiRain(type: String) {
        viewModelScope.launch {
            _emojiRainTrigger.emit(type)
        }
    }

    fun dismissAlert() {
        _alertEvent.value = null
    }

    private fun incrementKarma(amount: Int) {
        _karmaScore.value += amount
        sharedPrefs.edit().putInt("karma_score", _karmaScore.value).apply()
    }

    private fun incrementStreak() {
        _streakCount.value += 1
        sharedPrefs.edit().putInt("streak_count", _streakCount.value).apply()
        // Bonus karma on streak count milestones
        if (_streakCount.value % 5 == 0) {
            incrementKarma(50)
            _alertEvent.value = "Streak Milestone! You completed ${_streakCount.value} consecutive days. Karma +50 bonus awarded!"
        }
    }
}
