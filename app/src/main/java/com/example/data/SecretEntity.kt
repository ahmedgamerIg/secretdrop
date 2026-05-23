package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secrets")
data class SecretEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val title: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isModerated: Boolean = false,
    val moderationStatus: String = "APPROVED", // APPROVED, PENDING, FLAGGED
    val confidenceScore: Double = 1.0,
    val riskScore: Double = 0.0,
    val isPinned: Boolean = false,
    val isSecretOfDay: Boolean = false,
    val views: Int = 1,
    val likes: Int = 0,
    val streakDay: Int = 1,
    val botCreated: Boolean = false,
    // Fast reaction counts
    val reactLove: Int = 0,
    val reactSad: Int = 0,
    val reactDead: Int = 0,
    val reactFire: Int = 0,
    val reactShocked: Int = 0,
    val reactAngry: Int = 0,
    val reactWatching: Int = 0,
    val reactMindBlown: Int = 0,
    val reactFunny: Int = 0,
    val reactCold: Int = 0,
    val reactSupport: Int = 0,
    val reactRedFlag: Int = 0,
    val categoryTrendingScore: Double = 0.0,
    val userIpSimulated: String = "127.0.0.1"
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val secretId: Long,
    val parentId: Long? = null,
    val authorName: String,
    val authorColorHex: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isToxic: Boolean = false,
    val isPinned: Boolean = false,
    val likes: Int = 0,
    val reactLove: Int = 0,
    val reactSad: Int = 0,
    val reactDead: Int = 0,
    val reactFire: Int = 0,
    val reactShocked: Int = 0,
    val reactAngry: Int = 0,
    val reactWatching: Int = 0,
    val reactMindBlown: Int = 0,
    val reactFunny: Int = 0,
    val reactCold: Int = 0,
    val reactSupport: Int = 0,
    val reactRedFlag: Int = 0
)

@Entity(tableName = "user_reactions", primaryKeys = ["targetId", "targetType"])
data class UserReactionEntity(
    val targetId: Long,
    val targetType: String, // "SECRET" or "COMMENT"
    val reactionType: String
)

@Entity(tableName = "banned_spammers")
data class SpammerBanEntity(
    @PrimaryKey val simulatedIp: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetId: Long,
    val targetType: String, // "SECRET" or "COMMENT"
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val reporterIp: String = "127.0.0.1"
)
