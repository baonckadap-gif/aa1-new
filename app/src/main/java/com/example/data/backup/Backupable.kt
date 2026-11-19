package com.example.data.backup

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Kontrak generik untuk setiap model / entitas config agar wajib mendukung
 * serialisasi ke JSON Persistent Config, deserialisasi, dan migrasi schemaVersion.
 */
interface Backupable<T> {
  /** Kunci unik komponen config (mis. "settings", "profiles", "layouts", "midi_mapping"). */
  val backupKey: String

  /** Path file default di dalam folder backup SAF / .soundpadpro. */
  val defaultFilePath: String

  /** Versi skema JSON terkini yang dihasilkan serializer ini. */
  val schemaVersion: Int

  /** Serialisasi data domain menjadi JSONObject lengkap beserta field schemaVersion. */
  fun exportBackupJson(data: T): JSONObject

  /** Deserialisasi JSONObject menjadi data domain, otomatis memigrasi schemaVersion lama jika perlu. */
  fun importBackupJson(json: JSONObject): T

  /** Migrasi JSONObject dari [fromVersion] menuju [schemaVersion]. */
  fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject
}

/**
 * Kontrak kontributor untuk fitur baru pada fase berikutnya.
 * Setiap modul config baru cukup mengimplementasikan interface ini dan mendaftarkannya
 * ke [BackupSectionRegistry] agar otomatis ikut tersimpan & ter-restore di folder backup / .zip.
 */
interface BackupSectionContributor {
  val sectionKey: String
  val schemaVersion: Int
  fun exportSectionJson(): JSONObject
  fun importSectionJson(json: JSONObject, fromVersion: Int)
}

/**
 * Registry pusat untuk seluruh [BackupSectionContributor] tambahan dari fase berikutnya.
 */
object BackupSectionRegistry {
  private val contributors = ConcurrentHashMap<String, BackupSectionContributor>()

  fun register(contributor: BackupSectionContributor) {
    contributors[contributor.sectionKey] = contributor
  }

  fun unregister(sectionKey: String) {
    contributors.remove(sectionKey)
  }

  fun clear() {
    contributors.clear()
  }

  fun registeredKeys(): List<String> = contributors.keys.sorted()

  fun exportAllExtensions(): Map<String, JSONObject> {
    val result = linkedMapOf<String, JSONObject>()
    contributors.keys.sorted().forEach { key ->
      val contributor = contributors[key] ?: return@forEach
      runCatching {
        val payload = contributor.exportSectionJson()
        if (!payload.has("schemaVersion")) {
          payload.put("schemaVersion", contributor.schemaVersion)
        }
        result[key] = payload
      }
    }
    return result
  }

  fun importAllExtensions(extensions: Map<String, JSONObject>): List<String> {
    val warnings = mutableListOf<String>()
    extensions.forEach { (key, json) ->
      val contributor = contributors[key]
      if (contributor != null) {
        runCatching {
          val fromVersion = json.optInt("schemaVersion", 1)
          contributor.importSectionJson(json, fromVersion)
        }.onFailure { err ->
          warnings.add("Gagal memulihkan ekstensi '$key': ${err.message ?: "unknown error"}")
        }
      }
    }
    return warnings
  }
}
