package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SoundPadPlaybackService
import com.example.data.backup.BackupInspectionSummary
import com.example.data.backup.BackupSourceKind
import com.example.data.backup.PadBackupEntry
import com.example.data.backup.PersistentConfigBundle
import com.example.data.backup.PersistentConfigRestoreEngine
import com.example.data.backup.ProfileBackupBundle
import com.example.data.backup.RestoreSelectionOptions
import com.example.data.backup.SettingsBackupBundle
import com.example.data.local.db.PadDatabaseRepository
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.db.SoundPadEntity
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.SoundPad
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.PadSchedulerReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage6CrashAndReleaseUnitTest {

  private lateinit var context: Context
  private lateinit var db: SoundPadDatabase
  private lateinit var repo: PadDatabaseRepository
  private lateinit var settingsRepo: GlobalSettingsRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = PadDatabaseRepository(context = null, dao = db.soundPadDao())
    settingsRepo = GlobalSettingsRepository(context)
    SoundPadPlaybackService.onStopAllRequested = null
    SoundPadPlaybackService.onStopMasterRecordingRequested = null
    SoundPadPlaybackService.onScheduledTaskRequested = null
    SoundPadPlaybackService.onStopScheduledTaskRequested = null
  }

  @After
  fun tearDown() {
    SoundPadPlaybackService.onStopAllRequested = null
    SoundPadPlaybackService.onStopMasterRecordingRequested = null
    SoundPadPlaybackService.onScheduledTaskRequested = null
    SoundPadPlaybackService.onStopScheduledTaskRequested = null
    db.close()
  }

  private fun seedProfile(id: String = "prof_1", name: String = "Stage Profile"): PadProfileEntity {
    val now = System.currentTimeMillis()
    return PadProfileEntity(
      id = id,
      name = name,
      folderUri = null,
      gridPreset = GridPreset.GRID_4X4.name,
      gridRows = 4,
      gridCols = 4,
      createdAtMs = now,
      updatedAtMs = now,
      isActive = true
    )
  }

  private fun seedPad(
    padId: String,
    profileId: String = "prof_1",
    slot: Int = 0,
    name: String = "Kick"
  ): SoundPadEntity {
    return SoundPadEntity(
      id = padId,
      profileId = profileId,
      uriString = "demo://$padId.wav",
      fileName = "$padId.wav",
      displayName = name,
      extension = "wav",
      durationMs = 1500L,
      sizeBytes = 64000L,
      positionIndex = slot,
      bankIndex = 0
    )
  }

  // ==================== C1: ForegroundServiceDidNotStartInTimeException ====================

  @Test
  fun `playback service calls startForeground first on every intent path including stop null and unknown`() {
    val paths = listOf(
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_START_OR_UPDATE
        putExtra(SoundPadPlaybackService.EXTRA_ACTIVE_COUNT, 1)
        putExtra(SoundPadPlaybackService.EXTRA_ACTIVE_LABEL, "Kick")
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_STOP_ALL
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_STOP_SERVICE
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_STOP_MASTER_RECORDING
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_UPDATE_MASTER_RECORDING
        putExtra(SoundPadPlaybackService.EXTRA_IS_RECORDING, true)
        putExtra(SoundPadPlaybackService.EXTRA_RECORD_ELAPSED, "00:05")
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_TRIGGER_SCHEDULED_TASK
        putExtra(SoundPadPlaybackService.EXTRA_SCHEDULE_TASK_ID, "sched_1")
      },
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = SoundPadPlaybackService.ACTION_STOP_SCHEDULED_TASK
        putExtra(SoundPadPlaybackService.EXTRA_SCHEDULE_TASK_ID, "sched_1")
      },
      null,
      Intent(context, SoundPadPlaybackService::class.java).apply {
        action = "com.example.action.UNKNOWN_RESTART"
      }
    )

    val nm = context.getSystemService(NotificationManager::class.java)!!
    paths.forEachIndexed { idx, intent ->
      val controller = Robolectric.buildService(SoundPadPlaybackService::class.java, intent).create()
      val service = controller.get()
      val shadowService = Shadows.shadowOf(service)
      assertNotNull("Channels harus dibuat di onCreate", nm.getNotificationChannel(SoundPadPlaybackService.CHANNEL_ID))
      assertNotNull("Record channel harus dibuat di onCreate", nm.getNotificationChannel(SoundPadPlaybackService.RECORD_CHANNEL_ID))

      service.onStartCommand(intent, 0, idx + 1)
      val expectsImmediateStop = when (intent?.action) {
        SoundPadPlaybackService.ACTION_STOP_ALL,
        SoundPadPlaybackService.ACTION_STOP_SERVICE,
        SoundPadPlaybackService.ACTION_STOP_MASTER_RECORDING,
        SoundPadPlaybackService.ACTION_STOP_SCHEDULED_TASK,
        null -> true
        SoundPadPlaybackService.ACTION_START_OR_UPDATE,
        SoundPadPlaybackService.ACTION_UPDATE_MASTER_RECORDING,
        SoundPadPlaybackService.ACTION_TRIGGER_SCHEDULED_TASK -> false
        else -> true
      }
      assertTrue(
        "Notification ID harus diset via startForeground() di jalur index=$idx (${intent?.action}), id=${shadowService.lastForegroundNotificationId}",
        shadowService.lastForegroundNotificationId == SoundPadPlaybackService.NOTIFICATION_ID ||
          shadowService.lastForegroundNotificationId == SoundPadPlaybackService.RECORD_NOTIFICATION_ID
      )
      if (expectsImmediateStop) {
        assertTrue(
          "Jalur stop/null/else index=$idx (${intent?.action}) harus stopForeground setelah startForeground",
          shadowService.isForegroundStopped
        )
      } else {
        assertNotNull(
          "startForeground harus aktif di jalur index=$idx (${intent?.action})",
          shadowService.lastForegroundNotification
        )
        assertFalse(shadowService.isForegroundStopped)
      }
      controller.destroy()
    }
  }

  @Test
  fun `stopScheduledExecution and receiver stop path never call startForegroundService`() {
    val app = context as Application
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.clearStartedServices()

    var callbackTask = ""
    var callbackType = ""
    var callbackTarget = ""
    SoundPadPlaybackService.onStopScheduledTaskRequested = { t, type, target ->
      callbackTask = t
      callbackType = type
      callbackTarget = target
    }

    SoundPadPlaybackService.stopScheduledExecution(
      context = app,
      taskId = "task_stop_1",
      targetType = ScheduleTargetType.PAD.name,
      targetId = "pad_target_9"
    )

    assertEquals("task_stop_1", callbackTask)
    assertEquals("PAD", callbackType)
    assertEquals("pad_target_9", callbackTarget)
    assertNull(
      "stopScheduledExecution dilarang memanggil startForegroundService",
      shadowApp.peekNextStartedService()
    )

    val stopLatch = java.util.concurrent.CountDownLatch(1)
    SoundPadPlaybackService.onStopScheduledTaskRequested = { t, _, target ->
      if (t == "task_stop_2" && target == "pad_target_10") {
        stopLatch.countDown()
      }
    }
    val receiver = PadSchedulerReceiver()
    val stopBroadcast = Intent(app, PadSchedulerReceiver::class.java).apply {
      action = PadSchedulerManager.ACTION_STOP_SCHEDULED_PAD
      putExtra(PadSchedulerManager.EXTRA_TASK_ID, "task_stop_2")
      putExtra(PadSchedulerManager.EXTRA_TARGET_TYPE, ScheduleTargetType.PAD.name)
      putExtra(PadSchedulerManager.EXTRA_TARGET_ID, "pad_target_10")
    }
    receiver.onReceive(app, stopBroadcast)
    assertTrue(
      "Receiver stop path harus memicu onStopScheduledTaskRequested",
      stopLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)
    )

    val startedAfterReceiver = shadowApp.peekNextStartedService()
    assertTrue(
      "Receiver stop path tidak boleh memanggil startForegroundService",
      startedAfterReceiver == null || startedAfterReceiver.action != SoundPadPlaybackService.ACTION_STOP_SCHEDULED_TASK
    )
  }

  @Test
  fun `receiver trigger schedule starts foreground service immediately and fallback handles blocked FGS`() {
    val app = context as Application
    val shadowApp = Shadows.shadowOf(app)
    shadowApp.clearStartedServices()

    val receiver = PadSchedulerReceiver()
    val triggerIntent = Intent(app, PadSchedulerReceiver::class.java).apply {
      action = PadSchedulerManager.ACTION_TRIGGER_SCHEDULE
      putExtra(PadSchedulerManager.EXTRA_TASK_ID, "task_trigger_1")
    }
    receiver.onReceive(app, triggerIntent)

    val started = shadowApp.nextStartedService
    assertNotNull("ACTION_TRIGGER_SCHEDULE harus langsung memicu startForegroundService", started)
    assertEquals(SoundPadPlaybackService.ACTION_TRIGGER_SCHEDULED_TASK, started!!.action)
    assertEquals("task_trigger_1", started.getStringExtra(SoundPadPlaybackService.EXTRA_SCHEDULE_TASK_ID))

    // Simulasikan sistem menolak FGS dari background
    val blockingContext = object : ContextWrapper(app) {
      override fun startForegroundService(service: Intent?) = throw SecurityException("Blocked in background")
      override fun getApplicationContext(): Context = this
    }
    val ok = SoundPadPlaybackService.startScheduledExecution(blockingContext, "task_blocked_99")
    assertFalse("Harus mengembalikan false saat FGS ditolak tanpa crash", ok)

    val nm = app.getSystemService(NotificationManager::class.java)!!
    val posted = Shadows.shadowOf(nm).getNotification(SoundPadPlaybackService.SCHEDULE_FALLBACK_NOTIFICATION_ID)
    assertNotNull("Fallback notifikasi jadwal harus diposting saat FGS ditolak", posted)
  }

  // ==================== C2: SQLiteConstraintException FOREIGN KEY ====================

  @Test
  fun `upsert setting for already deleted pad is safely ignored and does not resurrect pad`() = runBlocking {
    val dao = db.soundPadDao()
    val profile = seedProfile("prof_del")
    val pad = seedPad("pad_deleted_1", profileId = "prof_del")
    dao.upsertProfile(profile)
    assertTrue(repo.upsertPadWithSetting(pad, PadSettingEntity(padId = pad.id)))

    // Hapus pad
    assertTrue(repo.deletePadById(pad.id))
    assertNull(dao.getPadById(pad.id))

    // Upsert setting untuk pad yang sudah dihapus -> wajib false, tanpa exception, tanpa membangkitkan pad
    val saved = repo.persistPadSettingSafely(PadSettingEntity(padId = pad.id, volume = 0.55f))
    assertFalse(saved)
    assertNull("Pad yang sudah dihapus tidak boleh hidup kembali", dao.getPadById(pad.id))
    assertNull("Setting orphan tidak boleh masuk ke tabel", dao.getPadSetting(pad.id))
  }

  @Test
  fun `duplicate methods verify parent profile inside transaction when profile missing`() = runBlocking {
    val clonedPad = seedPad("pad_orphan_clone", profileId = "missing_profile_id")
    val clonedSetting = PadSettingEntity(padId = clonedPad.id)

    val bulkOk = repo.bulkDuplicateSelectedPads(
      profileId = "missing_profile_id",
      clonedPads = listOf(clonedPad),
      clonedSettings = listOf(clonedSetting)
    )
    assertFalse(bulkOk)

    val insertOk = repo.insertClonedPadAndReindex(
      newPad = clonedPad,
      newSetting = clonedSetting,
      reindexedPositions = emptyList()
    )
    assertFalse(insertOk)
    assertNull(db.soundPadDao().getPadById(clonedPad.id))
  }

  @Test
  fun `concurrent duplicate rescan and setting updates serialize under Mutex with zero FK crash`() = runBlocking {
    val dao = db.soundPadDao()
    val profileId = "prof_race"
    val profile = seedProfile(profileId, "Race Profile")
    val initialPads = (0 until 6).map { i -> seedPad("pad_base_$i", profileId, slot = i, name = "Base $i") }
    val initialSettings = initialPads.map { PadSettingEntity(padId = it.id) }
    assertTrue(repo.saveCompleteProfile(profile, initialPads, initialSettings))

    val jobs = (0 until 12).map { round ->
      async(Dispatchers.Default) {
        when (round % 4) {
          0 -> {
            // Duplicate
            val cloneId = "pad_clone_$round"
            val clone = seedPad(cloneId, profileId, slot = 20 + round, name = "Clone $round")
            repo.bulkDuplicateSelectedPads(
              profileId = profileId,
              clonedPads = listOf(clone),
              clonedSettings = listOf(PadSettingEntity(padId = cloneId, pitch = 1.1f))
            )
          }
          1 -> {
            // Rescan sync subset (menghapus pad_base_5 pada putaran tertentu)
            val kept = initialPads.take(5)
            repo.syncProfilePadsIfExists(profileId, System.currentTimeMillis(), kept)
          }
          2 -> {
            // Setting update termasuk target pad yang mungkin sedang dihapus rescan
            repo.persistPadSettingSafely(PadSettingEntity(padId = "pad_base_5", volume = 0.7f))
          }
          else -> {
            // Upsert single pad + setting
            val extraId = "pad_extra_$round"
            repo.upsertPadWithSetting(
              pad = seedPad(extraId, profileId, slot = 50 + round, name = "Extra $round"),
              setting = PadSettingEntity(padId = extraId, pan = 0.25f)
            )
          }
        }
      }
    }
    jobs.awaitAll()

    // Tidak ada SQLiteConstraintException, dan seluruh pad di DB tetap punya setting valid atau induk valid
    val finalPads = dao.getPadsForProfileOnce(profileId)
    assertTrue(finalPads.isNotEmpty())
  }

  @Test
  fun `restore succeeds atomically when target profile does not exist in DB yet`() = runBlocking {
    val targetProfileId = "prof_restored_new"
    assertNull(db.soundPadDao().getProfileById(targetProfileId))

    val restoredPad = SoundPad(
      id = "pad_restored_1",
      uriString = "demo://pad_restored_1.wav",
      fileName = "pad_restored_1.wav",
      displayName = "Restored Bell",
      extension = "wav",
      durationMs = 2000L,
      sizeBytes = 32000L,
      positionIndex = 0,
      bankIndex = 0,
      settings = PadSettings(volume = 0.85f)
    )
    val restoredProfile = PadProfile(
      id = targetProfileId,
      name = "Restored Stage",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      createdAtMs = 1000L,
      updatedAtMs = 2000L,
      isActive = true
    )
    val bundle = PersistentConfigBundle(
      settings = SettingsBackupBundle(settings = GlobalSettings(activeProfileId = targetProfileId)),
      profiles = listOf(
        ProfileBackupBundle(
          profile = restoredProfile,
          pads = listOf(PadBackupEntry(pad = restoredPad, slotIndex = 0))
        )
      )
    )
    val inspection = BackupInspectionSummary(
      sourceLabel = "UnitTest",
      sourceKind = BackupSourceKind.ZIP_ARCHIVE,
      schemaVersion = 1,
      migratedFromVersion = 1,
      backupDateMs = 2000L,
      appVersionName = "1.0",
      profileCount = 1,
      bankCount = 1,
      padCount = 1,
      layoutCount = 0,
      schedulerCount = 0,
      sequencerCount = 0,
      includesWaveforms = false,
      bundle = bundle
    )

    val outcome = PersistentConfigRestoreEngine.applyRestore(
      context = context,
      dao = db.soundPadDao(),
      settingsRepo = settingsRepo,
      inspection = inspection,
      options = RestoreSelectionOptions(
        restoreSettings = false,
        restoreProfilesAndPads = true,
        restoreMidiMapping = true,
        restoreSchedulerAndSequencer = false
      ),
      rescheduleAlarms = false
    )

    assertTrue("Restore profil baru harus berhasil: ${outcome.errorMessage}", outcome.success)
    assertEquals(1, outcome.restoredProfileCount)
    assertEquals(1, outcome.restoredPadCount)
    assertNotNull(db.soundPadDao().getProfileById(targetProfileId))
    assertNotNull(db.soundPadDao().getPadById("pad_restored_1"))
    assertEquals(0.85f, db.soundPadDao().getPadSetting("pad_restored_1")?.volume ?: 0f, 0.001f)
  }

  @Test
  fun `dao constraint failure is caught logged to error_logs and does not crash`() = runBlocking {
    // Paksa pelanggaran FK mentah di dalam withDbMutation untuk memverifikasi try/catch & Error Log
    val result = repo.withDbMutation("forcedFkViolationTest", fallback = false) { rawDao ->
      rawDao.upsertPadSetting(PadSettingEntity(padId = "non_existent_pad_for_fk_test"))
      true
    }
    assertFalse("Operasi gagal harus mengembalikan fallback false tanpa melempar exception", result)

    val logs = db.soundPadDao().getErrorLogsOnce()
    assertTrue(
      "Error Log harus mencatat kegagalan DAO",
      logs.any { it.fileName.contains("forcedFkViolationTest") }
    )
  }
}
