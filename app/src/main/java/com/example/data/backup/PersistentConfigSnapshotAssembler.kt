package com.example.data.backup

import android.content.Context
import com.example.audio.toDomain
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadDao
import com.example.data.local.db.SoundPadEntity
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.SoundPad
import com.example.scheduler.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mengumpulkan seluruh config dari Room DAO & GlobalSettings menjadi [PersistentConfigBundle]
 * siap tulis JSON di Dispatchers.IO, tanpa mengubah logika audio maupun skema Room.
 */
object PersistentConfigSnapshotAssembler {

  suspend fun captureFromDatabase(
    dao: SoundPadDao,
    settings: GlobalSettings,
    context: Context? = null,
    appVersionName: String = "1.0",
    appVersionCode: Int = 1,
    includeWaveforms: Boolean = settings.persistentIncludeWaveforms,
    computeFileHashes: Boolean = true,
    outputDeviceNameResolver: ((Int?) -> String?)? = null
  ): PersistentConfigBundle = withContext(Dispatchers.IO) {
    val profileEntities = dao.getAllProfilesOnce()
    val allPadNotes = mutableListOf<PadMidiNoteEntry>()
    val allCcMappings = mutableListOf<MidiCcMapping>()
    val allPcMappings = mutableListOf<MidiProgramBankMapping>()

    val profileBundles = profileEntities.map { pEntity ->
      val padEntities = dao.getPadsForProfileOnce(pEntity.id)
      val settingEntities = dao.getPadSettingsForProfileOnce(pEntity.id)
      val settingsMap = settingEntities.associateBy { it.padId }

      val ccMappings = dao.getMidiCcMappingsOnce(pEntity.id).mapNotNull { entity ->
        val target = runCatching { MidiCcTarget.valueOf(entity.target) }.getOrNull() ?: return@mapNotNull null
        MidiCcMapping(
          id = entity.id,
          profileId = entity.profileId,
          target = target,
          ccNumber = entity.ccNumber,
          channel = entity.channel
        )
      }
      allCcMappings.addAll(ccMappings)

      val pcMappings = dao.getMidiPcMappingsOnce(pEntity.id).map { entity ->
        MidiProgramBankMapping(
          id = entity.id,
          profileId = entity.profileId,
          programNumber = entity.programNumber,
          bankIndex = entity.bankIndex,
          channel = entity.channel
        )
      }
      allPcMappings.addAll(pcMappings)

      val domainProfile = pEntity.toBackupDomainProfile(padEntities.size)
      val banks = buildBanksForProfile(domainProfile, padEntities)

      val padEntries = padEntities.map { padEntity ->
        val pad = padEntity.toBackupDomainSoundPad(settingsMap[padEntity.id])
        val sha1 = if (computeFileHashes && context != null && pad.uriString.isNotBlank()) {
          FileHashAndPathHelper.computeQuickFileSha1(context, pad.uriString, pad.sizeBytes)
        } else {
          ""
        }
        val rootUri = domainProfile.bankFolderUris[pad.bankIndex] ?: domainProfile.folderUri ?: settings.folderUri
        val relPath = FileHashAndPathHelper.extractRelativePath(
          rootTreeUriString = rootUri,
          subfolderName = pad.subfolderName,
          fileName = pad.fileName,
          documentUriString = pad.uriString
        )
        pad.midiNote?.let { note ->
          allPadNotes.add(
            PadMidiNoteEntry(
              profileId = domainProfile.id,
              padId = pad.id,
              padName = pad.displayName,
              fileName = pad.fileName,
              slotIndex = pad.positionIndex,
              bankIndex = pad.bankIndex,
              midiNote = note
            )
          )
        }
        PadBackupEntry(
          pad = pad,
          slotIndex = pad.positionIndex,
          fileMetadata = PadFileMetadata(
            padId = pad.id,
            uriString = pad.uriString,
            relativePath = relPath,
            fileName = pad.fileName,
            sizeBytes = pad.sizeBytes,
            sha1Hash = sha1,
            durationMs = pad.durationMs,
            extension = pad.extension
          ),
          isFileMissing = pad.errorType == PadErrorType.MISSING_PERMISSION
        )
      }

      ProfileBackupBundle(
        profile = domainProfile,
        banks = banks,
        pads = padEntries,
        ccMappings = ccMappings,
        pcMappings = pcMappings
      )
    }

    val scheduledTasks = dao.getAllScheduledTasksOnce().map { it.toDomain() }
    val playlists = dao.getAllSequencerPlaylistsOnce().map { it.toDomain() }
    val seqNotes = playlists.mapNotNull { pl ->
      pl.triggerMidiNote?.let { note ->
        SequencerMidiNoteEntry(
          playlistId = pl.id,
          playlistName = pl.name,
          midiNote = note
        )
      }
    }

    val layouts = PersistentConfigBundleSerializer.deriveLayoutsFromProfiles(profileBundles)
    val presets = PersistentConfigBundleSerializer.deriveDefaultPresets(settings, profileBundles)
    val mainDeviceName = settings.preferredMainOutputName ?: outputDeviceNameResolver?.invoke(settings.preferredMainOutputId)
    val cueDeviceName = settings.preferredCueOutputName ?: outputDeviceNameResolver?.invoke(settings.preferredCueOutputId)

    val settingsBundle = SettingsBackupBundle(
      settings = settings.copy(
        preferredMainOutputName = mainDeviceName,
        preferredCueOutputName = cueDeviceName
      ),
      audioOutput = AudioOutputConfigBackup(
        mainOutputDeviceId = settings.preferredMainOutputId,
        mainOutputDeviceName = mainDeviceName,
        mainOutputProductName = settings.preferredMainOutputProductName,
        mainOutputDeviceType = settings.preferredMainOutputType,
        mainOutputAddress = settings.preferredMainOutputAddress,
        cueOutputDeviceId = settings.preferredCueOutputId,
        cueOutputDeviceName = cueDeviceName,
        cueOutputProductName = settings.preferredCueOutputProductName,
        cueOutputDeviceType = settings.preferredCueOutputType,
        cueOutputAddress = settings.preferredCueOutputAddress,
        cueVolume = settings.cueVolume,
        audioBufferSizeMs = settings.audioBufferSizeMs
      ),
      syncConfig = PersistentSyncConfigBackup(
        backupFolderUri = settings.persistentBackupFolderUri,
        backupFolderName = settings.persistentBackupFolderName,
        autoSyncEnabled = settings.persistentAutoSyncEnabled,
        lastSyncAtMs = settings.persistentLastSyncAtMs,
        includeWaveforms = includeWaveforms
      )
    )

    val now = System.currentTimeMillis()
    val meta = ConfigMetaJson(
      schemaVersion = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
      appVersionName = appVersionName,
      appVersionCode = appVersionCode,
      createdAtMs = now,
      updatedAtMs = now,
      lastSyncedAtMs = now,
      profileCount = profileBundles.size,
      bankCount = profileBundles.sumOf { maxOf(it.banks.size, it.pads.map { p -> p.pad.bankIndex }.distinct().size) },
      padCount = profileBundles.sumOf { it.pads.size },
      layoutCount = layouts.size,
      schedulerCount = scheduledTasks.size,
      sequencerCount = playlists.size,
      includesWaveforms = includeWaveforms
    )

    PersistentConfigBundle(
      meta = meta,
      settings = settingsBundle,
      profiles = profileBundles,
      layouts = layouts,
      presets = presets,
      midiMapping = MidiMappingBackupBundle(
        focusModeMidiNote = profileBundles.firstOrNull {
          it.profile.id == settings.activeProfileId || it.profile.isActive
        }?.profile?.focusModeMidiNote ?: settings.focusModeMidiNote,
        ccMappings = allCcMappings,
        pcMappings = allPcMappings,
        padNoteMappings = allPadNotes,
        sequencerNoteMappings = seqNotes
      ),
      scheduler = SchedulerBackupBundle(tasks = scheduledTasks),
      sequencer = SequencerBackupBundle(playlists = playlists),
      extensionSections = BackupSectionRegistry.exportAllExtensions()
    )
  }

  private fun buildBanksForProfile(
    profile: PadProfile,
    pads: List<SoundPadEntity>
  ): List<BankBackupEntry> {
    val ordered = linkedSetOf<Int>()
    ordered.addAll(profile.bankOrder)
    ordered.addAll(profile.bankNames.keys.sorted())
    ordered.addAll(profile.bankFolderUris.keys.sorted())
    pads.map { it.bankIndex }.sorted().forEach { ordered.add(it) }
    if (ordered.isEmpty()) return emptyList()

    return ordered.mapIndexed { orderPos, bankIdx ->
      val padSubfolder = pads.firstOrNull { it.bankIndex == bankIdx && it.subfolderName.isNotBlank() }?.subfolderName
      BankBackupEntry(
        bankIndex = bankIdx,
        name = profile.bankNames[bankIdx] ?: padSubfolder ?: "B${bankIdx + 1}",
        folderUri = profile.bankFolderUris[bankIdx],
        orderPosition = orderPos
      )
    }
  }

  private fun PadProfileEntity.toBackupDomainProfile(padCount: Int): PadProfile {
    val preset = runCatching { GridPreset.valueOf(gridPreset) }.getOrDefault(GridPreset.GRID_4X4)
    val eqPreset = runCatching { EqPreset.valueOf(masterEqPreset) }.getOrDefault(EqPreset.FLAT)
    return PadProfile(
      id = id,
      name = name,
      folderUri = folderUri,
      gridConfig = GridConfig(preset = preset, rows = gridRows, cols = gridCols),
      padCount = padCount,
      bankNames = ProfileJsonSerializer.decodeBankNamesString(bankNamesJson),
      bankOrder = ProfileJsonSerializer.decodeBankOrderString(bankOrderJson),
      bankFolderUris = ProfileJsonSerializer.decodeBankFolderUrisString(bankFolderUrisJson),
      masterEqEnabled = masterEqEnabled,
      masterEqPreset = eqPreset,
      masterEqBandsDb = ProfileJsonSerializer.decodeEqBandsString(masterEqBandsJson),
      createdAtMs = createdAtMs,
      updatedAtMs = updatedAtMs,
      isActive = isActive,
      focusModeMidiNote = focusModeMidiNote
    )
  }

  private fun SoundPadEntity.toBackupDomainSoundPad(setting: PadSettingEntity?): SoundPad {
    val padSettings = if (setting == null) {
      PadSettings()
    } else {
      PadSettings(
        volume = setting.volume,
        speed = setting.speed,
        pitch = setting.pitch,
        pan = setting.pan,
        isLooping = setting.isLooping,
        triggerMode = runCatching { PadTriggerMode.valueOf(setting.triggerMode) }.getOrDefault(PadTriggerMode.TOGGLE),
        chokeGroup = setting.chokeGroup,
        fadeInMs = setting.fadeInMs,
        fadeOutMs = setting.fadeOutMs,
        fadeCurve = runCatching { FadeCurve.valueOf(setting.fadeCurve) }.getOrDefault(FadeCurve.EQUAL_POWER),
        crossfadeEnabled = setting.crossfadeEnabled,
        trimStartMs = setting.trimStartMs,
        trimEndMs = setting.trimEndMs,
        isDuckingSource = setting.isDuckingSource,
        normalizedGain = setting.normalizedGain,
        eqEnabled = setting.eqEnabled,
        eqPreset = runCatching { EqPreset.valueOf(setting.eqPreset) }.getOrDefault(EqPreset.FLAT),
        eqBandsDb = ProfileJsonSerializer.decodeEqBandsString(setting.eqBandsJson),
        isReversed = setting.isReversed,
        fxPreset = runCatching { FxPreset.valueOf(setting.fxPreset) }.getOrDefault(FxPreset.OFF),
        echoEnabled = setting.echoEnabled,
        echoDelayMs = setting.echoDelayMs,
        echoFeedback = setting.echoFeedback,
        echoMix = setting.echoMix,
        reverbEnabled = setting.reverbEnabled,
        reverbRoomSize = setting.reverbRoomSize,
        reverbDamping = setting.reverbDamping,
        reverbMix = setting.reverbMix
      )
    }

    return SoundPad(
      id = id,
      uriString = uriString,
      fileName = fileName,
      displayName = displayName,
      extension = extension,
      durationMs = durationMs,
      sizeBytes = sizeBytes,
      positionIndex = positionIndex,
      bankIndex = bankIndex,
      subfolderName = subfolderName,
      colorArgb = colorArgb,
      glowColorArgb = glowColorArgb,
      glowIntensity = glowIntensity,
      shapeStyle = shapeStyle?.let { runCatching { PadShapeStyle.valueOf(it) }.getOrNull() },
      borderColorArgb = borderColorArgb,
      borderWidthDp = borderWidthDp,
      cornerRadiusDp = cornerRadiusDp,
      textColorArgb = textColorArgb,
      surfaceFinish = runCatching { PadSurfaceFinish.valueOf(surfaceFinish) }.getOrDefault(PadSurfaceFinish.CLASSIC),
      badgeIcon = runCatching { PadBadgeIcon.valueOf(badgeIcon) }.getOrDefault(PadBadgeIcon.NONE),
      showSlotNumber = showSlotNumber,
      showDurationLabel = showDurationLabel,
      isClone = isClone,
      midiNote = midiNote,
      isPreloadedInRam = false,
      isFavorite = isFavorite,
      categoryTag = categoryTag,
      miniWaveform = ProfileJsonSerializer.decodeMiniWaveform(miniWaveformJson),
      errorType = errorType?.let { runCatching { PadErrorType.valueOf(it) }.getOrNull() },
      errorMessage = errorMessage,
      settings = padSettings
    )
  }
}
