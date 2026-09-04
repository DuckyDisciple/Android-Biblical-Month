package com.experiencingyah.bibliCal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MonthStartEntity::class,
        YearDecisionEntity::class,
        UserEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BiblicalMonthDatabase : RoomDatabase() {
    abstract fun dao(): BiblicalMonthDao

    companion object {
        @Volatile private var INSTANCE: BiblicalMonthDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        epochDay INTEGER NOT NULL,
                        allDay INTEGER NOT NULL,
                        startMinutesFromMidnight INTEGER,
                        notes TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_user_events_epochDay ON user_events(epochDay)",
                )
            }
        }

        fun get(context: Context): BiblicalMonthDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BiblicalMonthDatabase::class.java,
                    "biblical_month.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
