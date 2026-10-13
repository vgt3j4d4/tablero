package net.pladum.tablero.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An applied command and what is needed to undo it. */
@Serializable
data class HistoryEntry(
    val command: Command,
    val before: GameSnapshot,
    val appliedAtNanos: Long,
    /** For [Command.AdjustGameClock]: the delta actually applied after clamping. */
    val appliedDeltaNanos: Long = 0,
)

/** Everything the engine needs to resume after the app is killed; persisted as JSON by tv-app. */
@Serializable
data class EngineState(
    val schemaVersion: Int = SCHEMA_VERSION,
    /** [TimeSource] instant of the export; used to stop the clock at its last value after a reboot. */
    val savedAtNanos: Long,
    val snapshot: GameSnapshot,
    val history: List<HistoryEntry>,
) {
    fun toJson(): String = StateJson.encodeToString(this)

    companion object {
        const val SCHEMA_VERSION = 1

        /** Throws if [json] is malformed or was written by a newer app version. */
        fun fromJson(json: String): EngineState {
            val state = StateJson.decodeFromString<EngineState>(json)
            require(state.schemaVersion <= SCHEMA_VERSION) { "Unsupported schema version ${state.schemaVersion}" }
            return state
        }
    }
}

private val StateJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
