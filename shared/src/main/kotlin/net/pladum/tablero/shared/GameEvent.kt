package net.pladum.tablero.shared

/** One-off happenings the UI reacts to (buzzer, overlays). State itself is read from [GameSnapshot]. */
sealed interface GameEvent {
    data object ClockStarted : GameEvent

    data object ClockStopped : GameEvent

    /** Emitted exactly once when the running clock reaches 0. */
    data object GameClockExpired : GameEvent

    /**
     * A [command] changed the state, from whichever input it came; the UI confirms it (`HOME +1 · 48 → 49`).
     * [ToggleClock][Command.ToggleClock] is reported as the start or stop it resolved to. [before] and [after] are
     * the snapshots around this command only, so a confirmation never reads a later change.
     */
    data class CommandApplied(val command: Command, val before: GameSnapshot, val after: GameSnapshot) : GameEvent

    /** [Command.Undo] reverted [command], the last one applied; [before] and [after] as in [CommandApplied]. */
    data class CommandUndone(val command: Command, val before: GameSnapshot, val after: GameSnapshot) : GameEvent
}
