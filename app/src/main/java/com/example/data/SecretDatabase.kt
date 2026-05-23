package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SecretEntity::class,
        CommentEntity::class,
        UserReactionEntity::class,
        SpammerBanEntity::class,
        ReportEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SecretDatabase : RoomDatabase() {
    abstract fun secretDao(): SecretDao

    companion object {
        @Volatile
        private var INSTANCE: SecretDatabase? = null

        fun getDatabase(context: Context): SecretDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecretDatabase::class.java,
                    "secret_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
