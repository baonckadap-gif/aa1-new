package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    PadProfileEntity::class,
    SoundPadEntity::class,
    PadSettingEntity::class,
    MidiCcMappingEntity::class,
    ErrorLogEntity::class,
    ScheduledTaskEntity::class,
    SequencerPlaylistEntity::class,
    MidiPcBankMappingEntity::class,
    SchedulerIdCounterEntity::class
  ],
  version = 13,
  exportSchema = true
)
abstract class SoundPadDatabase : RoomDatabase() {
  abstract fun soundPadDao(): SoundPadDao

  companion object {
    const val DATABASE_NAME = "soundpad_pro.db"

    @Volatile
    private var instance: SoundPadDatabase? = null

    val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN bankOrderJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN bankFolderUrisJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE sound_pads ADD COLUMN bankIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sound_pads ADD COLUMN subfolderName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sound_pads ADD COLUMN miniWaveformJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sound_pads ADD COLUMN errorType TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE sound_pads ADD COLUMN errorMessage TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN pan REAL NOT NULL DEFAULT 0.0")
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS error_logs (
            id TEXT NOT NULL PRIMARY KEY,
            timestampMs INTEGER NOT NULL,
            fileName TEXT NOT NULL,
            uriString TEXT DEFAULT NULL,
            errorType TEXT NOT NULL,
            message TEXT NOT NULL,
            suggestion TEXT NOT NULL,
            stackTrace TEXT NOT NULL DEFAULT '',
            padId TEXT DEFAULT NULL
          )
          """.trimIndent()
        )
      }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN masterEqEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN masterEqPreset TEXT NOT NULL DEFAULT 'FLAT'")
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN masterEqBandsJson TEXT NOT NULL DEFAULT '[0.0,0.0,0.0,0.0,0.0]'")

        db.execSQL("ALTER TABLE pad_settings ADD COLUMN eqEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN eqPreset TEXT NOT NULL DEFAULT 'FLAT'")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN eqBandsJson TEXT NOT NULL DEFAULT '[0.0,0.0,0.0,0.0,0.0]'")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN isReversed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN fxPreset TEXT NOT NULL DEFAULT 'OFF'")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN echoEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN echoDelayMs REAL NOT NULL DEFAULT 220.0")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN echoFeedback REAL NOT NULL DEFAULT 0.30")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN echoMix REAL NOT NULL DEFAULT 0.25")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN reverbEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN reverbRoomSize REAL NOT NULL DEFAULT 0.35")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN reverbDamping REAL NOT NULL DEFAULT 0.45")
        db.execSQL("ALTER TABLE pad_settings ADD COLUMN reverbMix REAL NOT NULL DEFAULT 0.25")
      }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS scheduled_tasks (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            targetType TEXT NOT NULL,
            targetId TEXT NOT NULL,
            hour INTEGER NOT NULL,
            minute INTEGER NOT NULL,
            repeatMode TEXT NOT NULL,
            daysOfWeekMask INTEGER NOT NULL,
            enabled INTEGER NOT NULL,
            volume REAL NOT NULL,
            fadeInMs INTEGER NOT NULL,
            autoStopMinutes INTEGER NOT NULL,
            loopPlayback INTEGER NOT NULL,
            nextTriggerAtMs INTEGER NOT NULL,
            createdAtMs INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS sequencer_playlists (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            padIdsJson TEXT NOT NULL DEFAULT '[]',
            mode TEXT NOT NULL DEFAULT 'SEQUENTIAL',
            gapSeconds REAL NOT NULL DEFAULT 0.0,
            crossfadeEnabled INTEGER NOT NULL DEFAULT 0,
            crossfadeDurationMs INTEGER NOT NULL DEFAULT 1500,
            triggerPadId TEXT DEFAULT NULL,
            triggerMidiNote INTEGER DEFAULT NULL,
            updatedAtMs INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS midi_pc_bank_mappings (
            id TEXT NOT NULL PRIMARY KEY,
            profileId TEXT NOT NULL,
            programNumber INTEGER NOT NULL,
            bankIndex INTEGER NOT NULL,
            channel INTEGER NOT NULL DEFAULT -1,
            FOREIGN KEY(profileId) REFERENCES pad_profiles(id) ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_midi_pc_bank_mappings_profileId ON midi_pc_bank_mappings(profileId)")
      }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scheduled_tasks ADD COLUMN requestCode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE scheduled_tasks SET requestCode = CAST(rowid AS INTEGER) + 99 WHERE requestCode <= 0")
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS scheduler_id_counter (
            id INTEGER NOT NULL PRIMARY KEY,
            nextRequestCode INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          INSERT OR REPLACE INTO scheduler_id_counter (id, nextRequestCode)
          VALUES (
            1,
            MAX(100, COALESCE((SELECT MAX(requestCode) + 1 FROM scheduled_tasks), 100))
          )
          """.trimIndent()
        )
      }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pad_profiles ADD COLUMN focusModeMidiNote INTEGER DEFAULT NULL")
      }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
      MIGRATION_8_9,
      MIGRATION_9_10,
      MIGRATION_10_11,
      MIGRATION_11_12,
      MIGRATION_12_13
    )

    fun createRestoreCallback(context: Context): Callback {
      val appCtx = context.applicationContext
      return object : Callback() {
        @Volatile
        private var pendingDestructiveRestore = false

        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
          super.onDestructiveMigration(db)
          // Room invokes onDestructiveMigration after dropping tables and BEFORE recreating them.
          // Flag here and perform the actual INSERT restore in onOpen() once v12 tables exist.
          pendingDestructiveRestore = true
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          if (pendingDestructiveRestore) {
            pendingDestructiveRestore = false
            SoundPadPreMigrationBackupManager.restoreLatestBackupIntoDatabase(appCtx, db)
          }
        }
      }
    }

    fun getInstance(context: Context): SoundPadDatabase {
      return instance ?: synchronized(this) {
        instance ?: run {
          val appCtx = context.applicationContext
          // Export legacy v1..v7 database content to PersistentConfig JSON before Room drops tables
          SoundPadPreMigrationBackupManager.backupIfDestructiveMigrationNeeded(
            context = appCtx,
            dbName = DATABASE_NAME,
            minIncrementalVersion = 8
          )
          Room.databaseBuilder(
            appCtx,
            SoundPadDatabase::class.java,
            DATABASE_NAME
          )
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7)
            .addCallback(createRestoreCallback(appCtx))
            .build()
            .also { instance = it }
        }
      }
    }
  }
}
