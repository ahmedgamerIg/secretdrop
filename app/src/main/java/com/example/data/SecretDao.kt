package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretDao {

    // --- SECRETS CORES ---
    @Query("SELECT * FROM secrets ORDER BY timestamp DESC")
    fun getAllSecretsFlow(): Flow<List<SecretEntity>>

    @Query("SELECT * FROM secrets WHERE moderationStatus = 'APPROVED' ORDER BY timestamp DESC")
    fun getApprovedSecretsFlow(): Flow<List<SecretEntity>>

    @Query("SELECT * FROM secrets WHERE isSecretOfDay = 1 LIMIT 1")
    fun getSecretOfDayFlow(): Flow<SecretEntity?>

    @Query("SELECT * FROM secrets WHERE id = :id")
    fun getSecretFlowMap(id: Long): Flow<SecretEntity?>

    @Query("SELECT * FROM secrets WHERE id = :id")
    suspend fun getSecretById(id: Long): SecretEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecret(secret: SecretEntity): Long

    @Update
    suspend fun updateSecret(secret: SecretEntity)

    @Query("DELETE FROM secrets WHERE id = :id")
    suspend fun deleteSecretById(id: Long)

    @Query("UPDATE secrets SET views = views + 1 WHERE id = :id")
    suspend fun incrementViews(id: Long)

    @Query("UPDATE secrets SET isSecretOfDay = 0")
    suspend fun clearSecretOfDay()

    @Query("UPDATE secrets SET isSecretOfDay = 1 WHERE id = :id")
    suspend fun setSecretOfDay(id: Long)

    // --- COMMENTS CORES ---
    @Query("SELECT * FROM comments WHERE secretId = :secretId ORDER BY timestamp ASC")
    fun getCommentsForSecretFlow(secretId: Long): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments ORDER BY timestamp DESC")
    fun getAllCommentsFlow(): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE id = :id")
    suspend fun getCommentById(id: Long): CommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity): Long

    @Update
    suspend fun updateComment(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteCommentById(id: Long)

    @Query("DELETE FROM comments WHERE secretId = :secretId")
    suspend fun deleteCommentsForSecret(secretId: Long)

    // --- REACTIONS MEMORY ---
    @Query("SELECT * FROM user_reactions WHERE targetId = :targetId AND targetType = :targetType")
    suspend fun getUserReaction(targetId: Long, targetType: String): UserReactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserReaction(reaction: UserReactionEntity)

    @Query("DELETE FROM user_reactions WHERE targetId = :targetId AND targetType = :targetType")
    suspend fun deleteUserReaction(targetId: Long, targetType: String)

    @Query("SELECT * FROM user_reactions")
    fun getAllUserReactionsFlow(): Flow<List<UserReactionEntity>>

    // --- SPAM SECURITY ---
    @Query("SELECT * FROM banned_spammers WHERE simulatedIp = :ip")
    suspend fun getBanByIp(ip: String): SpammerBanEntity?

    @Query("SELECT * FROM banned_spammers ORDER BY timestamp DESC")
    fun getBannedIpsFlow(): Flow<List<SpammerBanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBan(ban: SpammerBanEntity)

    @Query("DELETE FROM banned_spammers WHERE simulatedIp = :ip")
    suspend fun deleteBanByIp(ip: String)

    // --- REPORTS MANAGEMENT ---
    @Query("SELECT * FROM user_reports ORDER BY timestamp DESC")
    fun getAllReportsFlow(): Flow<List<ReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity): Long

    @Query("UPDATE user_reports SET isResolved = 1 WHERE id = :id")
    suspend fun resolveReport(id: Long)

    @Query("DELETE FROM user_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)
}
