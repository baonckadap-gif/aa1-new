package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupSyncStatus
import com.example.data.backup.ConfigMetaJson
import com.example.data.backup.ConfigTreeStorageBackend
import com.example.data.backup.LocalDirectoryConfigTreeStorageBackend
import com.example.data.backup.PersistentConfigBundle
import com.example.data.backup.PersistentConfigSchema
import com.example.data.backup.PersistentConfigSyncManager
import com.example.data.backup.ProfileBackupBundle
import com.example.data.backup.SafPersistentConfigStorage
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SnapshotTrigger
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.db.SoundPadEntity
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PadProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistentConfigBackupManagerTest {

  private lateinit var context: Context
  private lateinit var db: SoundPadDatabase
  private lateinit var settingsRepo: GlobalSettingsRepository
  private lateinit var tempRoot: File

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    tempRoot = File(context.cacheDir, "backup_mgr_test_${System.nanoTime()}").apply { mkdirs() }
    db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    settingsRepo = GlobalSettingsRepository(context)
    resetSettingsRepo()
    seedSampleDatabase()
  }

  @After
  fun tearDown() = runBlocking {
    resetSettingsRepo()
    db.close()
    tempRoot.deleteRecursively()
    Unit
  }

  private suspend fun resetSettingsRepo() {
    settingsRepo.setPersistentBackupFolder(null, null)
    settingsRepo.setPersistentAutoSyncEnabled(true)
    settingsRepo.setPersistentLastSyncAtMs(0L)
    settingsRepo.setPersistentIncludeWaveforms(false)
    settingsRepo.setHasPromptedBackupFolder(false)
    settingsRepo.setLastDailySnapshotDay("")
    settingsRepo.setLastBackedUpVersionCode(0)
    settingsRepo.setHasCompletedOnboarding(true)
    settingsRepo.setSelectedFolder("", "")
  }

  private suspend fun seedSampleDatabase() {
    val dao = db.soundPadDao()
    dao.upsertProfile(
      PadProfileEntity(
        id = "prof_stage_b",
        name = "Concert Live",
        folderUri = "content://tree/music_stage",
        gridPreset = GridPreset.GRID_4X4.name,
        gridRows = 4,
        gridCols = 4,
        bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(mapOf(0 to "Main")),
        bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(listOf(0)),
        createdAtMs = 1000L,
        updatedAtMs = 2000L,
        isActive = true
      )
    )
    dao.upsertPad(
      SoundPadEntity(
        id = "pad_stage_b_1",
        profileId = "prof_stage_b",
        uriString = "content://tree/music_stage/kick.wav",
        fileName = "kick.wav",
        displayName = "Kick",
        extension = "WAV",
        durationMs = 800L,
        sizeBytes = 64000L,
        positionIndex = 0,
        bankIndex = 0,
        subfolderName = "Drums",
        glowIntensity = 0f
      )
    )
  }

  @Test
  fun `atomic write leaves original valid file intact and removes temp file when rename fails`() {
    val backupDir = File(tempRoot, "atomic_dir").apply { mkdirs() }
    val initialBackend = LocalDirectoryConfigTreeStorageBackend(backupDir)

    val firstWrite = initialBackend.writeFileAtomic("settings.json", "{\"masterVolume\":0.9}")
    assertTrue(firstWrite.isSuccess)
    assertEquals("{\"masterVolume\":0.9}", initialBackend.readFileText("settings.json"))

    val failingRenameBackend = LocalDirectoryConfigTreeStorageBackend(
      rootDir = backupDir,
      preRenameHook = { target ->
        if (target == "settings.json") {
          error("Simulated I/O crash before atomic rename")
        }
      }
    )

    val secondWrite = failingRenameBackend.writeFileAtomic("settings.json", "{\"masterVolume\":0.1,\"corrupt\":true}")
    assertTrue(secondWrite.isFailure)

    // Original valid target must remain completely untouched
    assertEquals("{\"masterVolume\":0.9}", initialBackend.readFileText("settings.json"))

    // Temporary partial files must be cleaned up
    val staleTemps = backupDir.listFiles()?.filter { it.name.contains(".tmp.") }.orEmpty()
    assertTrue("Expected no leftover temp files, found: $staleTemps", staleTemps.isEmpty())
  }

  @Test
  fun `writeToBackends creates full SAF structure mirrors to soundpadpro and prunes stale profile files`() {
    val primaryDir = File(tempRoot, "SoundPadPro").apply { mkdirs() }
    val musicDir = File(tempRoot, "MyMusic").apply { mkdirs() }

    val primaryBackend = LocalDirectoryConfigTreeStorageBackend(primaryDir)
    val musicBackend = LocalDirectoryConfigTreeStorageBackend(musicDir)
    val storage = SafPersistentConfigStorage(context)

    // Pre-create stale profile & an existing snapshot that must NOT be pruned
    primaryBackend.writeFileAtomic("profiles/deleted_old_profile.json", "{\"stale\":true}")
    primaryBackend.writeFileAtomic("backups/snapshot_20261117_090000_000_manual.json", "{\"snap\":1}")

    val bundleV1 = PersistentConfigBundle(
      meta = ConfigMetaJson(
        schemaVersion = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
        createdAtMs = 1_760_000_000_000L,
        appVersionName = "1.0",
        appVersionCode = 1
      ),
      settings = SettingsBackupBundle(settings = GlobalSettings(masterVolume = 0.77f)),
      profiles = listOf(
        ProfileBackupBundle(
          profile = PadProfile(
            id = "p1",
            name = "Set Utama",
            folderUri = "content://tree/music",
            gridConfig = GridConfig(),
            padCount = 0,
            createdAtMs = 1000L,
            updatedAtMs = 2000L,
            isActive = true
          )
        )
      )
    )

    val result = storage.writeToBackends(
      primaryBackend = primaryBackend,
      musicBackend = musicBackend,
      bundle = bundleV1,
      createSnapshotTrigger = SnapshotTrigger.DAILY,
      snapshotTimestampMs = 1_760_000_000_000L
    )

    assertTrue(result.success)
    assertTrue(result.musicMirrorWritten)
    assertNotNull(result.snapshotWritten)
    assertEquals(2, result.totalSnapshotsRetained)

    // Primary SAF layout
    assertNotNull(primaryBackend.readFileText("config_meta.json"))
    assertNotNull(primaryBackend.readFileText("settings.json"))
    assertNotNull(primaryBackend.readFileText("midi_mapping.json"))
    assertNotNull(primaryBackend.readFileText("scheduler.json"))
    assertNotNull(primaryBackend.readFileText("sequencer.json"))
    assertNotNull(primaryBackend.readFileText("presets/eq_presets.json"))
    assertNotNull(primaryBackend.readFileText("presets/fx_presets.json"))
    assertNotNull(primaryBackend.readFileText("presets/fade_curves.json"))
    assertEquals(1, primaryBackend.listFiles("profiles").size)
    assertEquals(1, primaryBackend.listFiles("layouts").size)
    // Stale profile deleted, backup preserved
    assertNull(primaryBackend.readFileText("profiles/deleted_old_profile.json"))
    assertNotNull(primaryBackend.readFileText("backups/snapshot_20261117_090000_000_manual.json"))

    // Mirror inside music/.soundpadpro/
    val sub = PersistentConfigSchema.MUSIC_SUBFOLDER_NAME
    assertNotNull(musicBackend.readFileText("$sub/config.json"))
    assertNotNull(musicBackend.readFileText("$sub/config_meta.json"))
    assertNotNull(musicBackend.readFileText("$sub/settings.json"))
    assertNotNull(musicBackend.readFileText("$sub/mapping/midi_mapping.json"))
    assertNotNull(musicBackend.readFileText("$sub/scheduler.json"))
    assertNotNull(musicBackend.readFileText("$sub/sequencer.json"))
    assertEquals(1, musicBackend.listFiles("$sub/profiles").size)
  }

  @Test
  fun `snapshot rotation retains strictly newest 10 snapshots and deletes oldest`() {
    val primaryDir = File(tempRoot, "RotationDir").apply { mkdirs() }
    val backend = LocalDirectoryConfigTreeStorageBackend(primaryDir)
    val storage = SafPersistentConfigStorage(context)

    val baseTime = 1_760_000_000_000L
    repeat(13) { idx ->
      val (info, _, retained) = storage.writeSnapshotAndRotateOnBackend(
        backend = backend,
        trigger = SnapshotTrigger.MANUAL,
        combinedJson = "{\"index\":$idx}",
        maxSnapshots = PersistentConfigSchema.MAX_ROTATED_SNAPSHOTS,
        timestampMs = baseTime + (idx * 5_000L)
      )
      assertNotNull(info)
      assertTrue(retained <= PersistentConfigSchema.MAX_ROTATED_SNAPSHOTS)
    }

    val remaining = backend.listFiles(PersistentConfigSchema.DIR_BACKUPS)
      .sortedBy { it.fileName }
    assertEquals(10, remaining.size)
    // Oldest 3 (idx 0,1,2) must have been deleted; oldest kept must correspond to idx 3
    val oldestKeptContent = backend.readFileText("backups/${remaining.first().fileName}")
    assertEquals("{\"index\":3}", oldestKeptContent)
    val newestKeptContent = backend.readFileText("backups/${remaining.last().fileName}")
    assertEquals("{\"index\":12}", newestKeptContent)
  }

  @Test
  fun `syncManager debounces rapid dirty bursts into one write and does not loop infinitely`() = runBlocking {
    val backupDir = File(tempRoot, "DebounceBackup").apply { mkdirs() }
    val musicDir = File(tempRoot, "DebounceMusic").apply { mkdirs() }
    val backupUri = Uri.fromFile(backupDir)
    val musicUri = Uri.fromFile(musicDir)

    val backends = mapOf(
      backupUri to LocalDirectoryConfigTreeStorageBackend(backupDir),
      musicUri to LocalDirectoryConfigTreeStorageBackend(musicDir)
    )
    var writeCounter = AtomicInteger(0)
    val storage = createTestStorage(backends, onWrite = { writeCounter.incrementAndGet() })

    val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var fixedNowMs = 1_763_450_000_000L
    val manager = PersistentConfigSyncManager(
      context = context,
      dao = db.soundPadDao(),
      settingsRepo = settingsRepo,
      appScope = managerScope,
      storage = storage,
      debounceMs = 180L,
      appVersionName = "2.0",
      appVersionCode = 2,
      timeProvider = { fixedNowMs },
      postAccessLostNotification = {}
    )

    try {
      settingsRepo.setSelectedFolder(musicUri.toString(), "DebounceMusic")
      manager.start()
      assertTrue(manager.onBackupFolderSelected(backupUri))
      val writesAfterInitialSelect = writeCounter.get()
      assertEquals(1, writesAfterInitialSelect)

      // Burst 5 rapid edits within debounce window
      repeat(5) { i ->
        fixedNowMs += 20L
        manager.notifyConfigDirty("rapid_change_$i")
        delay(25L)
      }

      // Wait until debounce fires and saves
      withTimeout(5000L) {
        while (writeCounter.get() < writesAfterInitialSelect + 1 ||
          manager.backupState.value.status != BackupSyncStatus.SAVED
        ) {
          delay(25L)
        }
      }

      // Give extra margin to prove DataStore timestamp write does NOT trigger another save loop
      delay(450L)
      assertEquals(writesAfterInitialSelect + 1, writeCounter.get())
      assertEquals(BackupSyncStatus.SAVED, manager.backupState.value.status)
      assertTrue(manager.backupState.value.lastSyncAtMs > 0L)
    } finally {
      managerScope.cancel()
    }
  }

  @Test
  fun `syncManager triggers daily snapshot once per day pre-update snapshot and app exit snapshot`() = runBlocking {
    val backupDir = File(tempRoot, "LifecycleBackup").apply { mkdirs() }
    val backupUri = Uri.fromFile(backupDir)
    val backend = LocalDirectoryConfigTreeStorageBackend(backupDir)
    val storage = createTestStorage(mapOf(backupUri to backend))

    var currentNowMs = 1_763_424_000_000L // Day 1
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // First simulate v1 setup
    val scopeV1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mgrV1 = PersistentConfigSyncManager(
      context = context,
      dao = db.soundPadDao(),
      settingsRepo = settingsRepo,
      appScope = scopeV1,
      storage = storage,
      debounceMs = 100L,
      appVersionName = "1.0",
      appVersionCode = 1,
      timeProvider = { currentNowMs },
      dayKeyProvider = { ms -> dayFmt.format(Date(ms)) },
      exitSnapshotMinIntervalMs = 10_000L,
      postAccessLostNotification = {}
    )

    try {
      mgrV1.start()
      assertTrue(mgrV1.onBackupFolderSelected(backupUri))

      val snapsDay1 = backend.listFiles("backups")
      assertEquals(1, snapsDay1.size)
      assertEquals(SnapshotTrigger.DAILY.slug, snapsDay1.single().trigger)

      // Manual sync on same day (no explicit snapshot) must NOT duplicate daily snapshot
      currentNowMs += 2_000L
      val sameDaySync = mgrV1.syncNow(createSnapshot = false)
      assertTrue(sameDaySync.success)
      assertEquals(1, backend.listFiles("backups").size)

      // App exit snapshot after interval passes
      currentNowMs += 20_000L
      mgrV1.onAppExiting()
      withTimeout(5000L) {
        while (backend.listFiles("backups").size < 2) {
          delay(25L)
        }
      }
      assertTrue(backend.listFiles("backups").any { it.trigger == SnapshotTrigger.APP_EXIT.slug })
    } finally {
      scopeV1.cancel()
    }

    // Now simulate app upgraded to versionCode = 2 on the next day -> pre_update snapshot + daily
    currentNowMs += 86_400_000L
    val scopeV2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mgrV2 = PersistentConfigSyncManager(
      context = context,
      dao = db.soundPadDao(),
      settingsRepo = settingsRepo,
      appScope = scopeV2,
      storage = storage,
      debounceMs = 100L,
      appVersionName = "2.0",
      appVersionCode = 2,
      timeProvider = { currentNowMs },
      dayKeyProvider = { ms -> dayFmt.format(Date(ms)) },
      postAccessLostNotification = {}
    )
    try {
      mgrV2.start()
      withTimeout(5000L) {
        while (backend.listFiles("backups").none { it.trigger == SnapshotTrigger.PRE_UPDATE.slug }) {
          delay(25L)
        }
      }
      assertTrue(backend.listFiles("backups").any { it.trigger == SnapshotTrigger.PRE_UPDATE.slug })

      // Next sync on day 2 creates daily snapshot for day 2
      currentNowMs += 5_000L
      mgrV2.syncNow(createSnapshot = false)
      val dailySnapshots = backend.listFiles("backups").filter { it.trigger == SnapshotTrigger.DAILY.slug }
      assertEquals(2, dailySnapshots.size)
    } finally {
      scopeV2.cancel()
    }
  }

  @Test
  fun `syncManager detects lost folder notifies once recovers when restored and handles prompt dismissal`() = runBlocking {
    val backupDir = File(tempRoot, "LostFolder").apply { mkdirs() }
    val backupUri = Uri.fromFile(backupDir)
    val backend = LocalDirectoryConfigTreeStorageBackend(backupDir)
    val storage = createTestStorage(mapOf(backupUri to backend))

    val notifCount = AtomicInteger(0)
    var lostCallbackReason: String? = null
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val manager = PersistentConfigSyncManager(
      context = context,
      dao = db.soundPadDao(),
      settingsRepo = settingsRepo,
      appScope = scope,
      storage = storage,
      debounceMs = 100L,
      postAccessLostNotification = { notifCount.incrementAndGet() }
    )
    manager.onAccessLostCallback = { _, reason ->
      lostCallbackReason = reason
    }

    try {
      manager.start()
      withTimeout(5000L) {
        while (!manager.backupState.value.shouldShowFirstLaunchPrompt) {
          delay(20L)
        }
      }
      assertTrue(manager.backupState.value.shouldShowFirstLaunchPrompt)

      manager.dismissFirstLaunchPrompt()
      withTimeout(5000L) {
        while (manager.backupState.value.shouldShowFirstLaunchPrompt) {
          delay(20L)
        }
      }
      assertFalse(manager.backupState.value.shouldShowFirstLaunchPrompt)

      // Select valid folder first
      assertTrue(manager.onBackupFolderSelected(backupUri))
      assertEquals(BackupSyncStatus.SAVED, manager.backupState.value.status)

      // Delete folder to simulate user deleting/revoking SAF directory
      backupDir.deleteRecursively()
      val check1 = manager.verifyBackupFolderAccess()
      assertFalse(check1.isAccessible)
      assertEquals(BackupSyncStatus.ERROR_INACCESSIBLE, manager.backupState.value.status)
      assertEquals(
        PersistentConfigSyncManager.ERROR_FOLDER_INACCESSIBLE,
        manager.backupState.value.errorMessage
      )
      assertEquals(1, notifCount.get())
      assertNotNull(lostCallbackReason)

      // Second check while still missing must NOT spam duplicate notifications
      manager.verifyBackupFolderAccess()
      assertEquals(1, notifCount.get())

      // Recreate / re-grant folder access -> verifyBackupFolderAccess recovers status
      backupDir.mkdirs()
      val check2 = manager.verifyBackupFolderAccess()
      assertTrue(check2.isAccessible)
      assertEquals(BackupSyncStatus.SAVED, manager.backupState.value.status)
      assertNull(manager.backupState.value.errorMessage)
      assertEquals(backupUri.toString(), settingsRepo.settingsFlow.first().persistentBackupFolderUri)
    } finally {
      scope.cancel()
    }
  }

  private fun createTestStorage(
    backends: Map<Uri, ConfigTreeStorageBackend>,
    onWrite: (() -> Unit)? = null
  ): SafPersistentConfigStorage {
    return SafPersistentConfigStorage(
      context = context,
      safBackendFactory = { uri ->
        val raw = backends[uri] ?: LocalDirectoryConfigTreeStorageBackend(File(uri.path ?: tempRoot.absolutePath))
        val isPrimary = backends.keys.firstOrNull() == uri
        if (!isPrimary || onWrite == null) {
          raw
        } else {
          object : ConfigTreeStorageBackend by raw {
            override fun writeFileAtomic(relativePath: String, content: String): Result<Unit> {
              if (relativePath == PersistentConfigSchema.FILE_CONFIG_META) {
                onWrite()
              }
              return raw.writeFileAtomic(relativePath, content)
            }
          }
        }
      }
    )
  }
}
