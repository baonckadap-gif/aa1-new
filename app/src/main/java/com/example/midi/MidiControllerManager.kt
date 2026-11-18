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
import android.content.Context
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
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val midiManager: MidiManager? = runCatching {
    context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
  }.getOrNull()

  private val openedDevices = ConcurrentHashMap<Int, MidiDevice>()
  private val openedPorts = ConcurrentHashMap<Int, MutableList<Pair<MidiOutputPort, MidiReceiver>>>()

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

  private var bleScanJob: Job? = null
  private var activeLeScanner: BluetoothLeScanner? = null

  private val deviceCallback = object : MidiManager.DeviceCallback() {
    override fun onDeviceAdded(device: MidiDeviceInfo) {
      openMidiDevice(device)
      publishDevices()
    }

    override fun onDeviceRemoved(device: MidiDeviceInfo) {
      closeMidiDevice(device.id)
      publishDevices()
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
    val manager = midiManager ?: return
    val candidate = _discoveredBleDevices.value.firstOrNull { it.address == address } ?: return
    stopBleScan()
    _bleStatusMessage.value = "Connecting to ${candidate.name}..."
    runCatching {
      manager.openBluetoothDevice(
        candidate.device,
        { device ->
          if (device != null) {
            handleOpenedDevice(device)
            publishDevices()
            _bleStatusMessage.value = "Connected BLE MIDI: ${candidate.name}"
          } else {
            _bleStatusMessage.value = "Failed to open BLE MIDI: ${candidate.name}"
          }
        },
        mainHandler
      )
    }.onFailure { err ->
      _bleStatusMessage.value = err.localizedMessage ?: "Bluetooth connect permission missing"
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
    stopBleScan()
    midiManager?.let { manager ->
      runCatching { manager.unregisterDeviceCallback(deviceCallback) }
    }
    openedDevices.keys.toList().forEach { id -> closeMidiDevice(id) }
    scope.cancel()
  }
}
