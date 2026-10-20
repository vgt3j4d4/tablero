package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side
import java.util.Locale

private const val MINUTE_MS = 60_000L

/** How long OK must be held on EXIT to leave for Android TV. */
const val EXIT_HOLD_MS = 2_000L

/** The menu's rows, top to bottom; rows with the same [group] sit together between dividing lines. */
enum class MenuRow(val group: Int) {
    GAME_CLOCK(0),
    GAME_LENGTH(0),
    HOME_SCORE(1),
    AWAY_SCORE(1),
    NEW_GAME(2),
    LANGUAGE(2),
    EXIT(2),
}

/** The exit confirm screen's buttons, left to right. */
enum class ExitChoice { CANCEL, EXIT }

/**
 * The exit confirm screen (design 09).
 *
 * @param holdStartedNanos when OK went down on EXIT ([TimeSource][net.pladum.tablero.shared.TimeSource] time), or
 *   null while OK isn't held there.
 */
data class ExitConfirm(val focused: ExitChoice, val holdStartedNanos: Long? = null)

/** What the open menu shows; live values (clock, scores, game length when unchanged) come from the snapshot. */
data class MenuState(
    val focused: MenuRow,
    /** A game length chosen with ←/→ and not applied yet, or null to show the engine's. */
    val pendingGameLengthMs: Long?,
    /** The language the Language row shows: the one in use, or one chosen and not applied yet. */
    val language: Locale,
    /** The set game clock dialog, shown instead of the menu while open. */
    val setClock: SetClockEditor?,
    /** The exit confirm screen, shown instead of the menu while open. */
    val exitConfirm: ExitConfirm?,
)

/** What the keys drive while the menu is open. */
interface MenuKeys {
    /** The menu shows and owns the remote. */
    val isOpen: Boolean

    /** Back long press. */
    fun open()

    fun up()

    fun down()

    fun left()

    fun right()

    fun ok()

    /** OK released; holding it is what confirms the exit. */
    fun okReleased()

    fun back()
}

/**
 * The menu (design 06), its set game clock dialog (design 08) and its exit confirm screen (design 09). ↑/↓ move
 * between rows, ←/→ change the focused row's value, OK opens the focused row, Back closes the dialog or the menu.
 *
 * Scores change with every press (one [Command.Score] each, as on the scoreboard). Game length and language are
 * kept pending while their row is focused and applied when focus leaves the row or the menu closes: game length as
 * one [Command.SetGameLength], so stepping through minutes takes a single undo, and language once, because applying
 * it recreates the activity.
 *
 * Exit (design 09) only opens while the clock is stopped, on a confirm screen with CANCEL focused;
 * leaving takes OK held on EXIT for [EXIT_HOLD_MS]. Releasing OK sooner, moving the focus or Back cancels the hold.
 *
 * @param languages the bundled languages, in menu order; [language] is the one in use.
 * @param now the current [TimeSource][net.pladum.tablero.shared.TimeSource] time, which starts a hold.
 * @param postDelayed runs an action after a delay in ms, unless [removeCallbacks] drops it first.
 * @param exitToAndroidTv leaves for the device's home screen; the menu has already closed.
 * @param onChange gets what to show while the menu is open, null when it closes.
 */
class Menu(
    private val dispatch: (Command) -> Unit,
    private val snapshot: () -> GameSnapshot,
    private val languages: List<Locale>,
    private val language: Locale,
    private val applyLanguage: (Locale) -> Unit,
    private val now: () -> Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val exitToAndroidTv: () -> Unit,
    private val onChange: (MenuState?) -> Unit,
) : MenuKeys {
    /** The focused row; null while the menu is closed. */
    var focused: MenuRow? = null
        private set

    private var pendingGameLengthMs: Long? = null
    private var pendingLanguage: Locale? = null
    private var setClock: SetClockEditor? = null
    private var exitConfirm: ExitConfirm? = null
    private val finishHold = Runnable { exit() }

    override val isOpen: Boolean get() = focused != null

    override fun open() = open(MenuRow.entries.first())

    /** Opens the menu with [row] focused, e.g. after the activity was recreated with the menu open. */
    fun open(row: MenuRow) {
        if (isOpen) return
        focused = row
        notifyChange()
    }

    /** Applies pending values and closes the menu, dialog included. */
    fun close() {
        if (!isOpen) return
        focused = null
        setClock = null
        exitConfirm = null
        removeCallbacks(finishHold)
        applyPending()
        notifyChange()
    }

    /** Applies a pending game length now, e.g. before the activity stops; the menu stays open. */
    fun applyGameLength() {
        val pending = pendingGameLengthMs ?: return
        pendingGameLengthMs = null
        if (pending != snapshot().rules.gameLengthMs) dispatch(Command.SetGameLength(pending))
        notifyChange()
    }

    /** Cancels a hold on EXIT in progress, e.g. when the activity pauses and OK's release may never arrive. */
    fun cancelExitHold() {
        val confirm = exitConfirm ?: return
        if (confirm.holdStartedNanos == null) return
        removeCallbacks(finishHold)
        exitConfirm = confirm.copy(holdStartedNanos = null)
        notifyChange()
    }

    override fun up() {
        val editor = setClock
        when {
            exitConfirm != null -> Unit
            editor != null -> show(editor.change(1))
            else -> move(-1)
        }
    }

    override fun down() {
        val editor = setClock
        when {
            exitConfirm != null -> Unit
            editor != null -> show(editor.change(-1))
            else -> move(1)
        }
    }

    override fun left() {
        val editor = setClock
        when {
            exitConfirm != null -> focusExitChoice(ExitChoice.CANCEL)
            editor != null -> show(editor.focus(ClockField.MINUTES))
            else -> change(-1)
        }
    }

    override fun right() {
        val editor = setClock
        when {
            exitConfirm != null -> focusExitChoice(ExitChoice.EXIT)
            editor != null -> show(editor.focus(ClockField.SECONDS))
            else -> change(1)
        }
    }

    override fun ok() {
        val confirm = exitConfirm
        if (confirm != null) {
            when (confirm.focused) {
                ExitChoice.CANCEL -> closeExitConfirm()
                ExitChoice.EXIT -> startExitHold(confirm)
            }
            return
        }
        val editor = setClock
        if (editor != null) {
            if (editor.edited) dispatch(Command.SetGameClock(editor.remainingMs))
            show(null)
            return
        }
        when (focused) {
            MenuRow.GAME_CLOCK -> {
                val s = snapshot()
                if (!s.gameClock.running) show(SetClockEditor.of(s.gameClock.remainingNanos, s.rules))
            }
            MenuRow.NEW_GAME -> {
                dispatch(Command.NewGame)
                close()
            }
            MenuRow.EXIT -> if (!snapshot().gameClock.running) {
                exitConfirm = ExitConfirm(ExitChoice.CANCEL)
                notifyChange()
            }
            else -> Unit
        }
    }

    override fun okReleased() = cancelExitHold()

    override fun back() {
        when {
            exitConfirm != null -> closeExitConfirm()
            setClock != null -> show(null)
            else -> close()
        }
    }

    private fun move(delta: Int) {
        val row = focused ?: return
        val next = MenuRow.entries.getOrNull(row.ordinal + delta) ?: return
        focused = next
        applyPending()
        notifyChange()
    }

    private fun change(delta: Int) {
        when (focused) {
            MenuRow.GAME_LENGTH -> {
                val current = pendingGameLengthMs ?: snapshot().rules.gameLengthMs
                val next = stepGameLength(current, delta)
                if (next == current) return
                pendingGameLengthMs = next
                notifyChange()
            }
            MenuRow.HOME_SCORE -> dispatch(Command.Score(Side.LEFT, delta))
            MenuRow.AWAY_SCORE -> dispatch(Command.Score(Side.RIGHT, delta))
            MenuRow.LANGUAGE -> {
                if (languages.size < 2) return
                val current = languages.indexOf(pendingLanguage ?: language)
                pendingLanguage = languages[(current + delta).mod(languages.size)].takeIf { it != language }
                notifyChange()
            }
            MenuRow.GAME_CLOCK, MenuRow.NEW_GAME, MenuRow.EXIT, null -> Unit
        }
    }

    /** Pending values only exist while their row is focused, so this runs whenever focus leaves a row. */
    private fun applyPending() {
        val gameLength = pendingGameLengthMs
        pendingGameLengthMs = null
        if (gameLength != null && gameLength != snapshot().rules.gameLengthMs) {
            dispatch(Command.SetGameLength(gameLength))
        }
        val chosen = pendingLanguage ?: return
        pendingLanguage = null
        applyLanguage(chosen)
    }

    private fun show(editor: SetClockEditor?) {
        setClock = editor
        notifyChange()
    }

    private fun focusExitChoice(choice: ExitChoice) {
        val confirm = exitConfirm ?: return
        if (confirm.focused == choice) return
        removeCallbacks(finishHold)
        exitConfirm = ExitConfirm(choice)
        notifyChange()
    }

    private fun startExitHold(confirm: ExitConfirm) {
        if (confirm.holdStartedNanos != null) return
        exitConfirm = confirm.copy(holdStartedNanos = now())
        postDelayed(finishHold, EXIT_HOLD_MS)
        notifyChange()
    }

    private fun closeExitConfirm() {
        removeCallbacks(finishHold)
        exitConfirm = null
        notifyChange()
    }

    /** OK was held on EXIT long enough. The clock can't start meanwhile from the remote; a later controller might. */
    private fun exit() {
        if (exitConfirm?.holdStartedNanos == null) return
        if (snapshot().gameClock.running) {
            closeExitConfirm()
            return
        }
        close()
        exitToAndroidTv()
    }

    private fun notifyChange() = onChange(
        focused?.let { MenuState(it, pendingGameLengthMs, pendingLanguage ?: language, setClock, exitConfirm) },
    )
}

/**
 * The game length one ←/→ step away from [currentMs]: the previous or next whole minute, within the allowed range.
 * A length that isn't whole minutes (taken from the clock at a game's first start) steps to the nearest one.
 */
internal fun stepGameLength(currentMs: Long, delta: Int): Long {
    val minutes = if (delta > 0) currentMs / MINUTE_MS + 1 else (currentMs + MINUTE_MS - 1) / MINUTE_MS - 1
    return (minutes * MINUTE_MS).coerceIn(GameRules.MIN_GAME_LENGTH_MS, GameRules.MAX_GAME_LENGTH_MS)
}
