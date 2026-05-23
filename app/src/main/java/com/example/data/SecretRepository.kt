package com.example.data

import android.util.Log
import com.example.api.GeminiApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SecretRepository(private val secretDao: SecretDao) {

    val allSecrets: Flow<List<SecretEntity>> = secretDao.getAllSecretsFlow()
    val approvedSecrets: Flow<List<SecretEntity>> = secretDao.getApprovedSecretsFlow()
    val secretOfDay: Flow<SecretEntity?> = secretDao.getSecretOfDayFlow()
    val bannedIps: Flow<List<SpammerBanEntity>> = secretDao.getBannedIpsFlow()
    val userReactions: Flow<List<UserReactionEntity>> = secretDao.getAllUserReactionsFlow()
    val allReports: Flow<List<ReportEntity>> = secretDao.getAllReportsFlow()
    val allCommentsList: Flow<List<CommentEntity>> = secretDao.getAllCommentsFlow()

    fun getSecretFlow(id: Long): Flow<SecretEntity?> = secretDao.getSecretFlowMap(id)
    fun getCommentsForSecret(secretId: Long): Flow<List<CommentEntity>> = secretDao.getCommentsForSecretFlow(secretId)

    suspend fun incrementViews(id: Long) = withContext(Dispatchers.IO) {
        secretDao.incrementViews(id)
    }

    suspend fun deleteSecret(id: Long) = withContext(Dispatchers.IO) {
        secretDao.deleteSecretById(id)
        secretDao.deleteCommentsForSecret(id)
    }

    suspend fun deleteComment(id: Long) = withContext(Dispatchers.IO) {
        secretDao.deleteCommentById(id)
    }

    suspend fun banIp(ip: String, reason: String) = withContext(Dispatchers.IO) {
        secretDao.insertBan(SpammerBanEntity(simulatedIp = ip, reason = reason))
    }

    suspend fun unbanIp(ip: String) = withContext(Dispatchers.IO) {
        secretDao.deleteBanByIp(ip)
    }

    suspend fun makeSecretOfDay(id: Long) = withContext(Dispatchers.IO) {
        secretDao.clearSecretOfDay()
        secretDao.setSecretOfDay(id)
    }

    /**
     * Submits a new silent anonymous secret with automated AI safety checks,
     * smart profanity censoring, dramatic title formatting, and aura color allocations.
     */
    suspend fun submitSecret(
        text: String,
        userTitle: String,
        category: String,
        ip: String
    ): Result<SecretEntity> = withContext(Dispatchers.IO) {
        try {
            // Check if user IP is administratively blocked
            val ban = secretDao.getBanByIp(ip)
            if (ban != null) {
                return@withContext Result.failure(Exception("This device has been temporarily suspended from posting: ${ban.reason}"))
            }

            // Execute full Gemini analysis (or localized fallback if offline/no keys configured)
            val analysis = GeminiApiHelper.analyzeConfession(text)

            // Auto-moderation auto-flagging rule:
            val autoStatus = if (analysis.isApproved && analysis.parsedRisk < 0.75) {
                "APPROVED"
            } else if (analysis.parsedRisk >= 0.75 && analysis.parsedRisk < 0.9) {
                "PENDING" // Goes to hidden Admin moderating review queue!
            } else {
                "FLAGGED" // Auto-collapsed or completely hidden
            }

            // Prefer generated clickbait title if user left standard title empty
            val finalTitle = if (userTitle.trim().isEmpty() || userTitle == "My Confession") {
                analysis.dramaticTitle
            } else {
                userTitle
            }

            val finalCategory = if (category.isEmpty() || category == "Auto") {
                analysis.suggestedCategory
            } else {
                category
            }

            val entity = SecretEntity(
                text = analysis.censoredText.ifEmpty { text },
                title = finalTitle,
                category = finalCategory,
                moderationStatus = autoStatus,
                isModerated = true,
                confidenceScore = 1.0 - analysis.parsedRisk,
                riskScore = analysis.parsedRisk,
                userIpSimulated = ip,
                // Populate base reaction variables from AI sentiment analysis to immediately showcase mood metrics
                reactLove = (analysis.emotions["love"] ?: 0.0).toInt().coerceAtLeast(0),
                reactSad = (analysis.emotions["sad"] ?: 0.0).toInt().coerceAtLeast(0),
                reactDead = (analysis.emotions["dead"] ?: 0.0).toInt().coerceAtLeast(0),
                reactFire = (analysis.emotions["fire"] ?: 0.0).toInt().coerceAtLeast(0),
                reactShocked = (analysis.emotions["shocked"] ?: 0.0).toInt().coerceAtLeast(0),
                reactAngry = (analysis.emotions["angry"] ?: 0.0).toInt().coerceAtLeast(0),
                reactWatching = (analysis.emotions["watching"] ?: 0.0).toInt().coerceAtLeast(0),
                reactFunny = (analysis.emotions["funny"] ?: 0.0).toInt().coerceAtLeast(0),
                reactSupport = (analysis.emotions["support"] ?: 0.0).toInt().coerceAtLeast(0)
            )

            val newId = secretDao.insertSecret(entity)
            val saved = secretDao.getSecretById(newId)
            if (saved != null) {
                Result.success(saved)
            } else {
                Result.failure(Exception("Failure during database storage routing."))
            }
        } catch (e: Exception) {
            Log.e("SecretRepository", "Submission error", e)
            Result.failure(e)
        }
    }

    /**
     * Reacts once per item. If they have reacted before, toggle/replace that reaction!
     */
    suspend fun reactToSecret(secretId: Long, reactionType: String): Boolean = withContext(Dispatchers.IO) {
        val secret = secretDao.getSecretById(secretId) ?: return@withContext false
        val existing = secretDao.getUserReaction(secretId, "SECRET")

        if (existing != null) {
            // Already reacted to this secret! Let's decrement the old reaction, and increment the new one
            if (existing.reactionType == reactionType) {
                // Clicking the same emoji toggles it OFF:
                val toggledSecret = decrementSecretReaction(secret, existing.reactionType)
                secretDao.updateSecret(toggledSecret)
                secretDao.deleteUserReaction(secretId, "SECRET")
                return@withContext true
            } else {
                // Toggling to a DIFFERENT emoji:
                var updated = decrementSecretReaction(secret, existing.reactionType)
                updated = incrementSecretReaction(updated, reactionType)
                secretDao.updateSecret(updated)
                secretDao.insertUserReaction(UserReactionEntity(secretId, "SECRET", reactionType))
                return@withContext true
            }
        } else {
            // Fresh reaction!
            val updated = incrementSecretReaction(secret, reactionType)
            secretDao.updateSecret(updated)
            secretDao.insertUserReaction(UserReactionEntity(secretId, "SECRET", reactionType))
            return@withContext true
        }
    }

    private fun incrementSecretReaction(secret: SecretEntity, type: String): SecretEntity {
        return when (type) {
            "love" -> secret.copy(reactLove = secret.reactLove + 1)
            "sad" -> secret.copy(reactSad = secret.reactSad + 1)
            "dead" -> secret.copy(reactDead = secret.reactDead + 1)
            "fire" -> secret.copy(reactFire = secret.reactFire + 1)
            "shocked" -> secret.copy(reactShocked = secret.reactShocked + 1)
            "angry" -> secret.copy(reactAngry = secret.reactAngry + 1)
            "watching" -> secret.copy(reactWatching = secret.reactWatching + 1)
            "mind_blown" -> secret.copy(reactMindBlown = secret.reactMindBlown + 1)
            "funny" -> secret.copy(reactFunny = secret.reactFunny + 1)
            "cold" -> secret.copy(reactCold = secret.reactCold + 1)
            "support" -> secret.copy(reactSupport = secret.reactSupport + 1)
            "red_flag" -> secret.copy(reactRedFlag = secret.reactRedFlag + 1)
            else -> secret
        }
    }

    private fun decrementSecretReaction(secret: SecretEntity, type: String): SecretEntity {
        return when (type) {
            "love" -> secret.copy(reactLove = (secret.reactLove - 1).coerceAtLeast(0))
            "sad" -> secret.copy(reactSad = (secret.reactSad - 1).coerceAtLeast(0))
            "dead" -> secret.copy(reactDead = (secret.reactDead - 1).coerceAtLeast(0))
            "fire" -> secret.copy(reactFire = (secret.reactFire - 1).coerceAtLeast(0))
            "shocked" -> secret.copy(reactShocked = (secret.reactShocked - 1).coerceAtLeast(0))
            "angry" -> secret.copy(reactAngry = (secret.reactAngry - 1).coerceAtLeast(0))
            "watching" -> secret.copy(reactWatching = (secret.reactWatching - 1).coerceAtLeast(0))
            "mind_blown" -> secret.copy(reactMindBlown = (secret.reactMindBlown - 1).coerceAtLeast(0))
            "funny" -> secret.copy(reactFunny = (secret.reactFunny - 1).coerceAtLeast(0))
            "cold" -> secret.copy(reactCold = (secret.reactCold - 1).coerceAtLeast(0))
            "support" -> secret.copy(reactSupport = (secret.reactSupport - 1).coerceAtLeast(0))
            "red_flag" -> secret.copy(reactRedFlag = (secret.reactRedFlag - 1).coerceAtLeast(0))
            else -> secret
        }
    }

    /**
     * Posts an anonymous comment or threaded reply. Auto-screens toxic comment text.
     */
    suspend fun submitComment(
        secretId: Long,
        parentId: Long?,
        authorName: String,
        authorColorHex: String,
        text: String
    ): Result<CommentEntity> = withContext(Dispatchers.IO) {
        try {
            // Assess toxicity with local check
            val lowerComment = text.lowercase()
            val toxicityIndicators = listOf("kill you", "die", "scam", "retard", "idiot", "loser", "hate black", "hate jew")
            var isToxic = false
            for (word in toxicityIndicators) {
                if (lowerComment.contains(word)) {
                    isToxic = true
                }
            }

            // Simple core profanity filter on comments:
            var cleanText = text
            val mildProfanities = listOf("fuck", "shit", "bitch", "asshole")
            for (word in mildProfanities) {
                if (cleanText.lowercase().contains(word)) {
                    cleanText = cleanText.replace("(?i)$word".toRegex(), "****")
                }
            }

            val comment = CommentEntity(
                secretId = secretId,
                parentId = parentId,
                authorName = authorName,
                authorColorHex = authorColorHex,
                text = cleanText,
                isToxic = isToxic
            )

            val commentId = secretDao.insertComment(comment)
            val saved = secretDao.getCommentById(commentId)
            if (saved != null) {
                Result.success(saved)
            } else {
                Result.failure(Exception("Failure storing comment."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactToComment(commentId: Long, reactionType: String): Boolean = withContext(Dispatchers.IO) {
        val comment = secretDao.getCommentById(commentId) ?: return@withContext false
        val existing = secretDao.getUserReaction(commentId, "COMMENT")

        if (existing != null) {
            if (existing.reactionType == reactionType) {
                val updated = decrementCommentReaction(comment, reactionType)
                secretDao.updateComment(updated)
                secretDao.deleteUserReaction(commentId, "COMMENT")
            } else {
                var updated = decrementCommentReaction(comment, existing.reactionType)
                updated = incrementCommentReaction(updated, reactionType)
                secretDao.updateComment(updated)
                secretDao.insertUserReaction(UserReactionEntity(commentId, "COMMENT", reactionType))
            }
        } else {
            val updated = incrementCommentReaction(comment, reactionType)
            secretDao.updateComment(updated)
            secretDao.insertUserReaction(UserReactionEntity(commentId, "COMMENT", reactionType))
        }
        return@withContext true
    }

    private fun incrementCommentReaction(comment: CommentEntity, type: String): CommentEntity {
        return when (type) {
            "love" -> comment.copy(reactLove = comment.reactLove + 1)
            "sad" -> comment.copy(reactSad = comment.reactSad + 1)
            "dead" -> comment.copy(reactDead = comment.reactDead + 1)
            "fire" -> comment.copy(reactFire = comment.reactFire + 1)
            "shocked" -> comment.copy(reactShocked = comment.reactShocked + 1)
            "angry" -> comment.copy(reactAngry = comment.reactAngry + 1)
            "watching" -> comment.copy(reactWatching = comment.reactWatching + 1)
            "mind_blown" -> comment.copy(reactMindBlown = comment.reactMindBlown + 1)
            "funny" -> comment.copy(reactFunny = comment.reactFunny + 1)
            "cold" -> comment.copy(reactCold = comment.reactCold + 1)
            "support" -> comment.copy(reactSupport = comment.reactSupport + 1)
            "red_flag" -> comment.copy(reactRedFlag = comment.reactRedFlag + 1)
            else -> comment
        }
    }

    private fun decrementCommentReaction(comment: CommentEntity, type: String): CommentEntity {
        return when (type) {
            "love" -> comment.copy(reactLove = (comment.reactLove - 1).coerceAtLeast(0))
            "sad" -> comment.copy(reactSad = (comment.reactSad - 1).coerceAtLeast(0))
            "dead" -> comment.copy(reactDead = (comment.reactDead - 1).coerceAtLeast(0))
            "fire" -> comment.copy(reactFire = (comment.reactFire - 1).coerceAtLeast(0))
            "shocked" -> comment.copy(reactShocked = (comment.reactShocked - 1).coerceAtLeast(0))
            "angry" -> comment.copy(reactAngry = (comment.reactAngry - 1).coerceAtLeast(0))
            "watching" -> comment.copy(reactWatching = (comment.reactWatching - 1).coerceAtLeast(0))
            "mind_blown" -> comment.copy(reactMindBlown = (comment.reactMindBlown - 1).coerceAtLeast(0))
            "funny" -> comment.copy(reactFunny = (comment.reactFunny - 1).coerceAtLeast(0))
            "cold" -> comment.copy(reactCold = (comment.reactCold - 1).coerceAtLeast(0))
            "support" -> comment.copy(reactSupport = (comment.reactSupport - 1).coerceAtLeast(0))
            "red_flag" -> comment.copy(reactRedFlag = (comment.reactRedFlag - 1).coerceAtLeast(0))
            else -> comment
        }
    }

    suspend fun updateSecretDirect(secret: SecretEntity) = withContext(Dispatchers.IO) {
        secretDao.updateSecret(secret)
    }

    /**
     * Seeds the application with gorgeous, realistic cinematic confessions so the user instantly
     * witnesses dynamic visual telemetry, comment threads, floating interactions and glowing stats.
     */
    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = secretDao.getAllSecretsFlow().first().size
        if (count > 0) return@withContext

        Log.d("SecretRepository", "Database empty! Seeding gorgeous, high-fidelity sample confessions.")

        // 1. Silent Crush - LOVE Aura
        val s1 = secretDao.insertSecret(SecretEntity(
            title = "Midnight Coffee Echoes",
            text = "I've been ordering the exact same lavender latte for 6 months just to hear the cute barista say my name. Yesterday they wrote 'You have soft eyes' on the lid. My heart exploded, but I just mumbled 'thank you' and ran out of the shop like a coward. I'm 26 and still act like a high school child.",
            category = "Love",
            views = 142,
            likes = 34,
            streakDay = 12,
            botCreated = true,
            reactLove = 45, reactSad = 2, reactFire = 12, reactAngry = 0, reactShocked = 15,
            reactFunny = 28, reactSupport = 32, reactWatching = 8,
            moderationStatus = "APPROVED",
            isSecretOfDay = true
        ))

        // Comments for s1
        secretDao.insertComment(CommentEntity(
            secretId = s1,
            authorName = "Silent Panda",
            authorColorHex = "#FF4081",
            text = "Oh my goodness, please go back there tomorrow! This is literally the plot of a romance movie!",
            likes = 12, reactLove = 8, reactSupport = 5
        ))
        val r1 = secretDao.insertComment(CommentEntity(
            secretId = s1,
            parentId = 1L, // Nested
            authorName = "Shy Wolf",
            authorColorHex = "#7C4DFF",
            text = "Agree! If they noticed your eyes, they were 100% waiting for you to say something. Don't waste this chance!",
            likes = 4, reactLove = 3
        ))
        
        // 2. Regret / Career Story
        val s2 = secretDao.insertSecret(SecretEntity(
            title = "The Corporate Ghostwriter",
            text = "My boss took 100% of the credit for a machine learning pipeline I spent 4 sleepless nights building. He got promoted to VP last week. I wrote a hidden script that slows down our cloud compilation latency by 1.5 seconds every time he clicks compile from his IP. He thinks the systems are 'unstable' and panics daily. I'm leaving next month.",
            category = "Work",
            views = 312,
            likes = 94,
            streakDay = 22,
            botCreated = true,
            reactLove = 11, reactSad = 5, reactFire = 62, reactAngry = 3, reactShocked = 45,
            reactFunny = 82, reactSupport = 39, reactWatching = 21, reactRedFlag = 8,
            moderationStatus = "APPROVED"
        ))

        secretDao.insertComment(CommentEntity(
            secretId = s2,
            authorName = "Evil Meerkat",
            authorColorHex = "#00E5FF",
            text = "This is brilliant, petty revenge. Absolute legend.",
            likes = 29, reactFire = 11, reactSupport = 8
        ))

        // 3. Heartbreaking Secret
        val s3 = secretDao.insertSecret(SecretEntity(
            title = "Unsent Letters in Box #4",
            text = "My partner passed away from cancer two years ago. I still call their phone number every Sunday night just to hear their voice recording say: 'Leave a message, I'm probably doing something stupid'. Yesterday, the operator said: 'This line is no longer in service'. My last link to their sound is gone and I feel completely empty.",
            category = "Regret",
            views = 428,
            likes = 156,
            streakDay = 8,
            botCreated = true,
            reactLove = 120, reactSad = 210, reactFire = 2, reactAngry = 0, reactShocked = 8,
            reactFunny = 0, reactSupport = 175, reactWatching = 4,
            moderationStatus = "APPROVED"
        ))

        secretDao.insertComment(CommentEntity(
            secretId = s3,
            authorName = "Sorrowful Badger",
            authorColorHex = "#B0BEC5",
            text = "I am crying reading this. I'm sending you so much love and support. You are not alone, friend.",
            likes = 45, reactSad = 15, reactSupport = 28
        ))

        // 4. Highly Risky review queue demo post (PENDING to show moderation queue functionality)
        val s4 = secretDao.insertSecret(SecretEntity(
            title = "The Crypto Fraudster",
            text = "I bought thousands in shitcoins by making multiple accounts using generated fake identities and artificially pumped our token. Made over $50K but I feel like an absolute fraud. Nobody found out yet.",
            category = "Guilt",
            views = 28,
            likes = 0,
            botCreated = true,
            riskScore = 0.81, // high risk
            moderationStatus = "PENDING" // This puts it in the hidden admin queue!
        ))

        // Seed some admin logs
        secretDao.insertBan(SpammerBanEntity("192.168.0.35", "Spamming commercial referral links in comments.", System.currentTimeMillis() - 86400000))

        // Seed initial pending reports for demonstration of ticketing system
        secretDao.insertReport(ReportEntity(
            targetId = s3,
            targetType = "SECRET",
            reason = "Triggering and highly depressing. Needs trigger warnings.",
            reporterIp = "184.23.41.9"
        ))
    }

    suspend fun submitReport(targetId: Long, targetType: String, reason: String, reporterIp: String) = withContext(Dispatchers.IO) {
        secretDao.insertReport(ReportEntity(targetId = targetId, targetType = targetType, reason = reason, reporterIp = reporterIp))
    }

    suspend fun resolveReport(id: Long) = withContext(Dispatchers.IO) {
        secretDao.resolveReport(id)
    }

    suspend fun deleteReport(id: Long) = withContext(Dispatchers.IO) {
        secretDao.deleteReportById(id)
    }
}
