package net.pladum.tablero.shared

/** One-off happenings the UI reacts to (buzzer, overlays). State itself is read from [GameSnapshot]. */
sealed interface GameEvent {
    data object ClockStarted : GameEvent

    data object ClockStopped : GameEvent

    /** Emitted exactly once when the running clock reaches 0. */
    data object GameClockExpired : GameEvent
}
