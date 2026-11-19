package com.example.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.example.domain.model.MidiDeviceSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface MidiEvent {
  data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : MidiEvent
  data class NoteOff(val channel: Int, val note: Int) : MidiEvent
  data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiEvent
  data class ProgramChange(val channel: Int, val program: Int) : MidiEvent
}

data class BleMidiCandidate(
  val name: String,
  val address: String,
  val device: BluetoothDevice
)

enum class BleReconnectPhase {
  IDLE,
  WAITING_BLUETOOTH,
  SCANNING,
  CONNECTING,
  WAITING_BACKOFF,
  CONNECTED,
  EXHAUSTED
}

data class BleReconnectStatus(
  val savedAddress: String? = null,
  val savedName: String? = null,
  val autoReconnectEnabled: Boolean = true,
  val phase: BleReconnectPhase = BleReconnectPhase.IDLE,
  val attempt: Int = 0,
  val maxAttempts: Int = BleReconnectBackoffMath.MAX_ATTEMPTS,
  val nextRetryDelayMs: Long = 0L,
  val detailMessage: String? = null
)

object BleReconnectBackoffMath {
  const val MAX_ATTEMPTS = 8
  private val STEP_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 30_000L, 30_000L, 30_000L)

  fun computeDelayMs(attemptIndex: Int): Long {
    if (attemptIndex <= 0) return STEP_DELAYS_MS[0]
    return STEP_DELAYS_MS[attemptIndex.coerceIn(0, STEP_DELAYS_MS.lastIndex)]
  }
}

object MidiMessageParser {
  private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

  fun formatNoteName(note: Int): String {
    val clamped = note.coerceIn(0, 127)
    val octave = (clamped / 12) - 1
    return "${NOTE_NAMES[clamped % 12]}$octave ($clamped)"
  }

  fun parse(data: ByteArray, offset: Int, count: Int): List<MidiEvent> {
    if (count <= 0 || offset < 0 || offset >= data.size) return emptyList()
    val end = (offset + count).coerceAtMost(data.size)
    val events = mutableListOf<MidiEvent>()
    var i = offset
    var runningStatus = 0

    while (i < end) {
      val b = data[i].toInt() and 0xFF

      // System real-time messages (0xF8..0xFF): 1 byte, do not clear running status
      if (b >= 0xF8) {
        i++
        continue
      }

      // SysEx start (0xF0): skip until 0xF7 or packet end
      if (b == 0xF0) {
        runningStatus = 0
        i++
        while (i < end && (data[i].toInt() and 0xFF) != 0xF7) {
          i++
        }
        if (i < end) i++
        continue
      }

      // Other System Common messages (0xF1..0xF7)
      if (b >= 0xF1) {
        runningStatus = 0
        i += when (b) {
          0xF2 -> 3
          0xF1, 0xF3 -> 2
          else -> 1
        }
        continue
      }

      val status: Int
      if ((b and 0x80) != 0) {
        status = b
        runningStatus = status
        i++
      } else if (runningStatus != 0) {
        status = runningStatus
      } else {
        i++
        continue
      }

      val command = status and 0xF0
      val channel = status and 0x0F

      when (command) {
        0x80 -> { // Note Off
          if (i + 1 >= end) break
          val note = data[i].toInt() and 0x7F
          events.add(MidiEvent.NoteOff(channel = channel, note = note))
          i += 2
        }
        0x90 -> { // Note On (velocity 0 == Note Off)
          if (i + 1 >= end) break
          val note = data[i].toInt() and 0x7F
          val velocity = data[i + 1].toInt() and 0x7F
          if (velocity == 0) {
            events.add(MidiEvent.NoteOff(channel = channel, note = note))
          } else {
            events.add(MidiEvent.NoteOn(channel = channel, note = note, velocity = velocity))
          }
          i += 2
        }
        0xB0 -> { // Control Change
          if (i + 1 >= end) break
          val controller = data[i].toInt() and 0x7F
          val value = data[i + 1].toInt() and 0x7F
          events.add(MidiEvent.ControlChange(channel = channel, controller = controller, value = value))
          i += 2
        }
        0xA0, 0xE0 -> { // Poly Aftertouch / Pitch Bend (2 data bytes)
          if (i + 1 >= end) break
          i += 2
        }
        0xC0 -> { // Program Change (1 data byte)
          if (i >= end) break
          val program = data[i].toInt() and 0x7F
          events.add(MidiEvent.ProgramChange(channel = channel, program = program))
          i += 1
        }
        0xD0 -> { // Channel Pressure (1 data byte)
          if (i >= end) break
          i += 1
        }
        else -> {
          i++
        }
      }
    }
    return events
  }
}

class MidiControllerManager(private val context: Context) {

  companion object {
    val BLE_MIDI_SERVICE_UUID: ParcelUuid = ParcelUuid(
      UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    )
    private const val BLE_SCAN_TIMEOUT_MS = 12_000L
    private const val RECONNECT_SCAN_WINDOW_MS = 5_000L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val midiManager: MidiManager? = runCatching {
    context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
  }.getOrNull()

  private val openedDevices = ConcurrentHashMap<Int, MidiDevice>()
  private val openedPorts = ConcurrentHashMap<Int, MutableList<Pair<MidiOutputPort, MidiReceiver>>>()
  private val openedBleDeviceAddresses = ConcurrentHashMap<Int, Pair<String, String>>()

  private val _connectedDevices = MutableStateFlow<List<MidiDeviceSummary>>(emptyList())
  val connectedDevices: StateFlow<List<MidiDeviceSummary>> = _connectedDevices.asStateFlow()

  private val _midiEvents = MutableSharedFlow<MidiEvent>(extraBufferCapacity = 128)
  val midiEvents: SharedFlow<MidiEvent> = _midiEvents.asSharedFlow()

  private val _lastActivityLabel = MutableStateFlow<String?>(null)
  val lastActivityLabel: StateFlow<String?> = _lastActivityLabel.asStateFlow()

  private val _discoveredBleDevices = MutableStateFlow<List<BleMidiCandidate>>(emptyList())
  val discoveredBleDevices: StateFlow<List<BleMidiCandidate>> = _discoveredBleDevices.asStateFlow()

  private val _isScanningBle = MutableStateFlow(false)
  val isScanningBle: StateFlow<Boolean> = _isScanningBle.asStateFlow()

  private val _bleStatusMessage = MutableStateFlow<String?>(null)
  val bleStatusMessage: StateFlow<String?> = _bleStatusMessage.asStateFlow()

  private val _bleReconnectStatus = MutableStateFlow(BleReconnectStatus())
  val bleReconnectStatus: StateFlow<BleReconnectStatus> = _bleReconnectStatus.asStateFlow()

  var onBleDeviceConnected: ((address: String, name: String) -> Unit)? = null

  private var bleScanJob: Job? = null
  private var autoReconnectJob: Job? = null
  private var activeLeScanner: BluetoothLeScanner? = null
  private var savedBleAddress: String? = null
  private var savedBleName: String? = null
  private var autoReconnectEnabled: Boolean = true
  private var reconnectScanCallback: ScanCallback? = null

  private val btStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
      if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
      val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
      when (state) {
        BluetoothAdapter.STATE_ON -> {
          if (autoReconnectEnabled && !savedBleAddress.isNullOrBlank()) {
            scheduleAutoReconnect(resetAttempts = true)
          }
        }
        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
          cancelAutoReconnectScanOnly()
          autoReconnectJob?.cancel()
          autoReconnectJob = null
          if (!savedBleAddress.isNullOrBlank()) {
            _bleReconnectStatus.value = _bleReconnectStatus.value.copy(
              phase = BleReconnectPhase.WAITING_BLUETOOTH,
              detailMessage = "Bluetooth nonaktif — menunggu Bluetooth menyala"
            )
          }
        }
      }
    }
  }

  private val deviceCallback = object : MidiManager.DeviceCallback() {
    override fun onDeviceAdded(device: MidiDeviceInfo) {
      openMidiDevice(device)
      publishDevices()
    }

    override fun onDeviceRemoved(device: MidiDeviceInfo) {
      val removedBle = openedBleDeviceAddresses.remove(device.id)
      closeMidiDevice(device.id)
      publishDevices()
      val targetAddr = savedBleAddress
      if (removedBle != null && autoReconnectEnabled && !targetAddr.isNullOrBlank() &&
        removedBle.first.equals(targetAddr, ignoreCase = true)
      ) {
        scheduleAutoReconnect(resetAttempts = true)
      } else if (_connectedDevices.value.none { it.typeLabel == "Bluetooth" && it.isConnected } &&
        autoReconnectEnabled && !targetAddr.isNullOrBlank() && device.type == MidiDeviceInfo.TYPE_BLUETOOTH
      ) {
        scheduleAutoReconnect(resetAttempts = true)
      }
    }
  }

  private val bleScanCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val btDevice = result.device ?: return
      val address = btDevice.address ?: return
      val name = runCatching { btDevice.name }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        ?: "BLE MIDI ($address)"

      val candidate = BleMidiCandidate(name = name, address = address, device = btDevice)
      val current = _discoveredBleDevices.value
      if (current.none { it.address == address }) {
        _discoveredBleDevices.value = current + candidate
      }

      if (autoReconnectEnabled && savedBleAddress?.equals(address, ignoreCase = true) == true &&
        !isSavedBleCurrentlyConnected()
      ) {
        attemptConnectBluetoothDevice(btDevice, name, address, fromAutoReconnect = true)
      }
    }

    override fun onScanFailed(errorCode: Int) {
      _isScanningBle.value = false
      _bleStatusMessage.value = "Bluetooth MIDI scan failed (code $errorCode)"
    }
  }

  init {
    val manager = midiManager
    if (manager != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        runCatching {
          manager.registerDeviceCallback(
            MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
            { runnable -> mainHandler.post(runnable) },
            deviceCallback
          )
        }.onFailure {
          @Suppress("DEPRECATION")
          manager.registerDeviceCallback(deviceCallback, mainHandler)
        }
      } else {
        @Suppress("DEPRECATION")
        manager.registerDeviceCallback(deviceCallback, mainHandler)
      }
      refreshDevices()
    }
    runCatching {
      val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(btStateReceiver, filter, Context.RECEIVER_EXPORTED)
      } else {
        @Suppress("DEPRECATION")
        context.registerReceiver(btStateReceiver, filter)
      }
    }
  }

  fun isMidiSupported(): Boolean = midiManager != null

  fun refreshDevices() {
    val manager = midiManager ?: return
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
    } else {
      @Suppress("DEPRECATION")
      manager.devices.toList()
    }
    infos.forEach { info ->
      if (!openedDevices.containsKey(info.id)) {
        openMidiDevice(info)
      }
    }
    publishDevices()
  }

  @SuppressLint("MissingPermission")
  fun startBleScan() {
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? = btManager?.adapter
    if (adapter == null || !adapter.isEnabled) {
      _bleStatusMessage.value = "Enable Bluetooth first to scan BLE MIDI devices"
      return
    }
    val scanner = adapter.bluetoothLeScanner
    if (scanner == null) {
      _bleStatusMessage.value = "BLE Scanner unavailable on this device"
      return
    }

    stopBleScan()
    _discoveredBleDevices.value = emptyList()
    _bleStatusMessage.value = null

    val filters = listOf(
      ScanFilter.Builder().setServiceUuid(BLE_MIDI_SERVICE_UUID).build()
    )
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()

    runCatching {
      scanner.startScan(filters, settings, bleScanCallback)
      activeLeScanner = scanner
      _isScanningBle.value = true
      bleScanJob = scope.launch {
        delay(BLE_SCAN_TIMEOUT_MS)
        stopBleScan()
      }
    }.onFailure { err ->
      _isScanningBle.value = false
      _bleStatusMessage.value = err.localizedMessage ?: "Bluetooth permission required for BLE MIDI"
    }
  }

  @SuppressLint("MissingPermission")
  fun stopBleScan() {
    bleScanJob?.cancel()
    bleScanJob = null
    if (_isScanningBle.value) {
      runCatching { activeLeScanner?.stopScan(bleScanCallback) }
    }
    _isScanningBle.value = false
  }

  @SuppressLint("MissingPermission")
  fun connectBleDevice(address: String) {
    val candidate = _discoveredBleDevices.value.firstOrNull { it.address == address } ?: return
    stopBleScan()
    attemptConnectBluetoothDevice(candidate.device, candidate.name, candidate.address, fromAutoReconnect = false)
  }

  fun configureAutoReconnect(
    address: String?,
    name: String?,
    enabled: Boolean
  ) {
    val cleanAddress = address?.trim()?.takeIf { it.isNotEmpty() }
    val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
    val configChanged = cleanAddress != savedBleAddress ||
      cleanName != savedBleName ||
      enabled != autoReconnectEnabled
    savedBleAddress = cleanAddress
    savedBleName = cleanName
    autoReconnectEnabled = enabled

    if (cleanAddress == null) {
      autoReconnectJob?.cancel()
      autoReconnectJob = null
      cancelAutoReconnectScanOnly()
      _bleReconnectStatus.value = BleReconnectStatus(
        savedAddress = null,
        savedName = null,
        autoReconnectEnabled = enabled,
        phase = BleReconnectPhase.IDLE,
        detailMessage = null
      )
      return
    }

    if (isSavedBleCurrentlyConnected()) {
      _bleReconnectStatus.value = BleReconnectStatus(
        savedAddress = cleanAddress,
        savedName = cleanName ?: cleanAddress,
        autoReconnectEnabled = enabled,
        phase = BleReconnectPhase.CONNECTED,
        detailMessage = "Terhubung ke ${cleanName ?: cleanAddress}"
      )
      return
    }

    if (!enabled) {
      autoReconnectJob?.cancel()
      autoReconnectJob = null
      cancelAutoReconnectScanOnly()
      _bleReconnectStatus.value = BleReconnectStatus(
        savedAddress = cleanAddress,
        savedName = cleanName ?: cleanAddress,
        autoReconnectEnabled = false,
        phase = BleReconnectPhase.IDLE,
        detailMessage = "Auto-reconnect nonaktif untuk ${cleanName ?: cleanAddress}"
      )
      return
    }

    if (configChanged || autoReconnectJob?.isActive != true) {
      scheduleAutoReconnect(resetAttempts = configChanged)
    }
  }

  fun triggerAutoReconnectNow() {
    if (savedBleAddress.isNullOrBlank()) return
    autoReconnectEnabled = true
    scheduleAutoReconnect(resetAttempts = true)
  }

  fun forgetSavedBleDevice() {
    savedBleAddress = null
    savedBleName = null
    autoReconnectJob?.cancel()
    autoReconnectJob = null
    cancelAutoReconnectScanOnly()
    _bleReconnectStatus.value = BleReconnectStatus(
      savedAddress = null,
      savedName = null,
      autoReconnectEnabled = autoReconnectEnabled,
      phase = BleReconnectPhase.IDLE,
      detailMessage = null
    )
  }

  private fun isSavedBleCurrentlyConnected(): Boolean {
    val target = savedBleAddress ?: return false
    if (openedBleDeviceAddresses.values.any { it.first.equals(target, ignoreCase = true) }) {
      return true
    }
    return false
  }

  @SuppressLint("MissingPermission")
  private fun scheduleAutoReconnect(resetAttempts: Boolean) {
    val targetAddr = savedBleAddress ?: return
    val targetLabel = savedBleName ?: targetAddr
    if (!autoReconnectEnabled || isSavedBleCurrentlyConnected()) return

    if (resetAttempts) {
      autoReconnectJob?.cancel()
    } else if (autoReconnectJob?.isActive == true) {
      return
    }

    autoReconnectJob = scope.launch {
      var attempt = 0
      while (attempt < BleReconnectBackoffMath.MAX_ATTEMPTS &&
        autoReconnectEnabled &&
        savedBleAddress?.equals(targetAddr, ignoreCase = true) == true &&
        !isSavedBleCurrentlyConnected()
      ) {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null) {
          _bleReconnectStatus.value = BleReconnectStatus(
            savedAddress = targetAddr,
            savedName = targetLabel,
            autoReconnectEnabled = autoReconnectEnabled,
            phase = BleReconnectPhase.WAITING_BLUETOOTH,
            attempt = attempt,
            detailMessage = "Adaptor Bluetooth tidak tersedia"
          )
          return@launch
        }
        if (!adapter.isEnabled) {
          _bleReconnectStatus.value = BleReconnectStatus(
            savedAddress = targetAddr,
            savedName = targetLabel,
            autoReconnectEnabled = autoReconnectEnabled,
            phase = BleReconnectPhase.WAITING_BLUETOOTH,
            attempt = attempt,
            detailMessage = "Bluetooth mati — akan sambung ulang saat Bluetooth ON"
          )
          return@launch
        }

        attempt++
        // 1) Try bonded/cached remote BluetoothDevice directly first
        val directDevice = runCatching {
          val bonded = adapter.bondedDevices?.firstOrNull { it.address.equals(targetAddr, ignoreCase = true) }
          bonded ?: adapter.getRemoteDevice(targetAddr)
        }.getOrNull()

        var foundViaScan = false
        if (directDevice != null && attempt == 1) {
          _bleReconnectStatus.value = BleReconnectStatus(
            savedAddress = targetAddr,
            savedName = targetLabel,
            autoReconnectEnabled = true,
            phase = BleReconnectPhase.CONNECTING,
            attempt = attempt,
            detailMessage = "Menghubungkan ulang $targetLabel (#$attempt)..."
          )
          attemptConnectBluetoothDevice(directDevice, targetLabel, targetAddr, fromAutoReconnect = true)
          delay(2_200L)
          if (isSavedBleCurrentlyConnected()) return@launch
        }

        // 2) Scan for matching BLE MIDI device address when device powers on / advertises
        val scanner = adapter.bluetoothLeScanner
        if (scanner != null && !isSavedBleCurrentlyConnected()) {
          _bleReconnectStatus.value = BleReconnectStatus(
            savedAddress = targetAddr,
            savedName = targetLabel,
            autoReconnectEnabled = true,
            phase = BleReconnectPhase.SCANNING,
            attempt = attempt,
            detailMessage = "Mencari $targetLabel di BLE MIDI (#$attempt/${BleReconnectBackoffMath.MAX_ATTEMPTS})..."
          )
          val seenDevice = scanOnceForTargetAddress(scanner, targetAddr, RECONNECT_SCAN_WINDOW_MS)
          if (seenDevice != null) {
            foundViaScan = true
            _bleReconnectStatus.value = BleReconnectStatus(
              savedAddress = targetAddr,
              savedName = targetLabel,
              autoReconnectEnabled = true,
              phase = BleReconnectPhase.CONNECTING,
              attempt = attempt,
              detailMessage = "$targetLabel ditemukan, menghubungkan..."
            )
            attemptConnectBluetoothDevice(seenDevice, targetLabel, targetAddr, fromAutoReconnect = true)
            delay(2_500L)
            if (isSavedBleCurrentlyConnected()) return@launch
          }
        }

        if (attempt >= BleReconnectBackoffMath.MAX_ATTEMPTS) {
          _bleReconnectStatus.value = BleReconnectStatus(
            savedAddress = targetAddr,
            savedName = targetLabel,
            autoReconnectEnabled = true,
            phase = BleReconnectPhase.EXHAUSTED,
            attempt = attempt,
            detailMessage = "$targetLabel belum merespons. Ketuk Sambung Ulang saat perangkat menyala."
          )
          return@launch
        }

        val backoffMs = BleReconnectBackoffMath.computeDelayMs(if (foundViaScan) attempt - 1 else attempt)
        _bleReconnectStatus.value = BleReconnectStatus(
          savedAddress = targetAddr,
          savedName = targetLabel,
          autoReconnectEnabled = true,
          phase = BleReconnectPhase.WAITING_BACKOFF,
          attempt = attempt,
          nextRetryDelayMs = backoffMs,
          detailMessage = "Coba ulang $targetLabel dalam ${backoffMs / 1000L} dtk (#$attempt/${BleReconnectBackoffMath.MAX_ATTEMPTS})"
        )
        delay(backoffMs)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun scanOnceForTargetAddress(
    scanner: BluetoothLeScanner,
    targetAddress: String,
    windowMs: Long
  ): BluetoothDevice? {
    var matchedDevice: BluetoothDevice? = null
    val cb = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val dev = result.device ?: return
        val addr = dev.address ?: return
        val name = runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() }
          ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
          ?: "BLE MIDI ($addr)"
        val candidate = BleMidiCandidate(name = name, address = addr, device = dev)
        val cur = _discoveredBleDevices.value
        if (cur.none { it.address == addr }) {
          _discoveredBleDevices.value = cur + candidate
        }
        if (addr.equals(targetAddress, ignoreCase = true)) {
          matchedDevice = dev
        }
      }
    }
    reconnectScanCallback = cb
    val started = runCatching {
      val filters = listOf(ScanFilter.Builder().setServiceUuid(BLE_MIDI_SERVICE_UUID).build())
      val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
      scanner.startScan(filters, settings, cb)
      true
    }.getOrDefault(false)

    if (!started) {
      reconnectScanCallback = null
      return null
    }

    val startAt = System.currentTimeMillis()
    while (matchedDevice == null && (System.currentTimeMillis() - startAt) < windowMs) {
      delay(250L)
    }
    runCatching { scanner.stopScan(cb) }
    if (reconnectScanCallback === cb) {
      reconnectScanCallback = null
    }
    return matchedDevice
  }

  @SuppressLint("MissingPermission")
  private fun cancelAutoReconnectScanOnly() {
    val cb = reconnectScanCallback ?: return
    reconnectScanCallback = null
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    runCatching { btManager?.adapter?.bluetoothLeScanner?.stopScan(cb) }
  }

  @SuppressLint("MissingPermission")
  private fun attemptConnectBluetoothDevice(
    btDevice: BluetoothDevice,
    displayName: String,
    address: String,
    fromAutoReconnect: Boolean
  ) {
    val manager = midiManager ?: return
    if (!fromAutoReconnect) {
      _bleStatusMessage.value = "Connecting to $displayName..."
      _bleReconnectStatus.value = _bleReconnectStatus.value.copy(
        savedAddress = address,
        savedName = displayName,
        phase = BleReconnectPhase.CONNECTING,
        detailMessage = "Menghubungkan ke $displayName..."
      )
    }
    runCatching {
      manager.openBluetoothDevice(
        btDevice,
        { device ->
          if (device != null) {
            openedBleDeviceAddresses[device.info.id] = address to displayName
            savedBleAddress = address
            savedBleName = displayName
            handleOpenedDevice(device)
            publishDevices()
            onBleDeviceConnected?.invoke(address, displayName)
            _bleStatusMessage.value = "Connected BLE MIDI: $displayName"
            _bleReconnectStatus.value = BleReconnectStatus(
              savedAddress = address,
              savedName = displayName,
              autoReconnectEnabled = autoReconnectEnabled,
              phase = BleReconnectPhase.CONNECTED,
              attempt = 0,
              detailMessage = "Terhubung ke $displayName"
            )
          } else if (!fromAutoReconnect) {
            _bleStatusMessage.value = "Failed to open BLE MIDI: $displayName"
          }
        },
        mainHandler
      )
    }.onFailure { err ->
      if (!fromAutoReconnect) {
        _bleStatusMessage.value = err.localizedMessage ?: "Bluetooth connect permission missing"
      } else {
        _bleReconnectStatus.value = _bleReconnectStatus.value.copy(
          phase = BleReconnectPhase.WAITING_BACKOFF,
          detailMessage = err.localizedMessage ?: "Izin Bluetooth diperlukan untuk auto-reconnect"
        )
      }
    }
  }

  fun clearBleMessage() {
    _bleStatusMessage.value = null
  }

  private fun openMidiDevice(info: MidiDeviceInfo) {
    val manager = midiManager ?: return
    val hasOutputPort = info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
    if (!hasOutputPort) return

    manager.openDevice(
      info,
      { device ->
        if (device != null) {
          handleOpenedDevice(device)
          publishDevices()
        }
      },
      mainHandler
    )
  }

  private fun handleOpenedDevice(device: MidiDevice) {
    val info = device.info
    closeMidiDevice(info.id)
    openedDevices[info.id] = device

    val portReceivers = mutableListOf<Pair<MidiOutputPort, MidiReceiver>>()
    info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { portInfo ->
      val outPort = device.openOutputPort(portInfo.portNumber) ?: return@forEach
      val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
          val parsed = MidiMessageParser.parse(msg, offset, count)
          if (parsed.isNotEmpty()) {
            scope.launch {
              parsed.forEach { event ->
                _lastActivityLabel.value = when (event) {
                  is MidiEvent.NoteOn -> "Note On ${MidiMessageParser.formatNoteName(event.note)} vel ${event.velocity}"
                  is MidiEvent.NoteOff -> "Note Off ${MidiMessageParser.formatNoteName(event.note)}"
                  is MidiEvent.ControlChange -> "CC #${event.controller} = ${event.value} (Ch ${event.channel + 1})"
                  is MidiEvent.ProgramChange -> "Program Change #${event.program} (Ch ${event.channel + 1})"
                }
                _midiEvents.emit(event)
              }
            }
          }
        }
      }
      outPort.connect(receiver)
      portReceivers.add(outPort to receiver)
    }
    openedPorts[info.id] = portReceivers
  }

  private fun closeMidiDevice(deviceId: Int) {
    openedBleDeviceAddresses.remove(deviceId)
    openedPorts.remove(deviceId)?.forEach { (port, receiver) ->
      runCatching { port.disconnect(receiver) }
      runCatching { port.close() }
    }
    openedDevices.remove(deviceId)?.let { device ->
      runCatching { device.close() }
    }
  }

  private fun publishDevices() {
    val manager = midiManager ?: run {
      _connectedDevices.value = emptyList()
      return
    }
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
    } else {
      @Suppress("DEPRECATION")
      manager.devices.toList()
    }
    _connectedDevices.value = infos
      .filter { info -> info.ports.any { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT } }
      .map { info ->
        val props = info.properties
        val product = props.getString(MidiDeviceInfo.PROPERTY_NAME)
          ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
          ?: "MIDI Device #${info.id}"
        val typeLabel = when (info.type) {
          MidiDeviceInfo.TYPE_USB -> "USB OTG"
          MidiDeviceInfo.TYPE_BLUETOOTH -> "Bluetooth"
          MidiDeviceInfo.TYPE_VIRTUAL -> "Virtual"
          else -> "MIDI"
        }
        MidiDeviceSummary(
          id = info.id,
          name = product,
          typeLabel = typeLabel,
          isConnected = openedDevices.containsKey(info.id)
        )
      }
  }

  fun release() {
    autoReconnectJob?.cancel()
    autoReconnectJob = null
    cancelAutoReconnectScanOnly()
    stopBleScan()
    runCatching { context.unregisterReceiver(btStateReceiver) }
    midiManager?.let { manager ->
      runCatching { manager.unregisterDeviceCallback(deviceCallback) }
    }
    openedDevices.keys.toList().forEach { id -> closeMidiDevice(id) }
    scope.cancel()
  }
}
