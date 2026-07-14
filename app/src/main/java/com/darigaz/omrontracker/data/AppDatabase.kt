package com.darigaz.omrontracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Measurement::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: coluna de controle de sync com o repositório GitHub. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE measurements " +
                        "ADD COLUMN syncedToGitHub INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omron-tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
