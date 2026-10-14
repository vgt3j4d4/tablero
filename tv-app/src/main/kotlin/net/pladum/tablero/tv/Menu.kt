package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side
import java.util.Locale

private const val MINUTE_MS = 60_000L

/** The menu's rows, top to bottom; rows with the same [group] sit together between dividing lines. */
enum class MenuRow(val group: Int) {
    GAME_CLOCK(0),
    GAME_LENGTH(0),
    HOME_SCORE(1),
    AWAY_SCORE(1),
    NEW_GAME(2),
    LANGUAGE(2),
}

/** What the open menu shows; live values (clock, scores, game length when unchanged) come from the snapshot. */
data class MenuState(
    val focused: MenuRow,
    /** A game length chosen with ←/→ and not applied yet, or null to show the engine's. */
    val pendingGameLengthMs: Long?,
    /** The language the Language row shows: the one in use, or one chosen and not applied yet. */
    val language: Locale,
    /** The set game clock dialog, shown instead of the menu while open. */
    val setClock: SetClockEditor?,
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

    fun back()
}

/**
 * The menu (design 06) and its set game clock dialog (design 08). ↑/↓ move between rows, ←/→ change the focused
 * row's value, OK opens the focused row, Back closes the dialog or the menu.
 *
 * Scores change with every press (one [Command.Score] each, as on the scoreboard). Game length and language are
 * kept pending while their row is focused and applied when focus leaves the row or the menu closes: game length as
 * one [Command.SetGameLength], so stepping through minutes takes a single undo, and language once, because applying
 * it recreates the activity.
 *
 * @param languages the bundled languages, in menu order; [language] is the one in use.
 * @param onChange gets what to show while the menu is open, null when it closes.
 */
class Menu(
    private val dispatch: (Command) -> Unit,
    private val snapshot: () -> GameSnapshot,
    private val languages: List<Locale>,
    private val language: Locale,
    private val applyLanguage: (Locale) -> Unit,
    private val onChange: (MenuState?) -> Unit,
) : MenuKeys {
    /** The focused row; null while the menu is closed. */
    var focused: MenuRow? = null
        private set

    private var pendingGameLengthMs: Long? = null
    private var pendingLanguage: Locale? = null
    private var setClock: SetClockEditor? = null

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

    override fun up() {
        val editor = setClock
        if (editor != null) show(editor.change(1)) else move(-1)
    }

    override fun down() {
        val editor = setClock
        if (editor != null) show(editor.change(-1)) else move(1)
    }

    override fun left() {
        val editor = setClock
        if (editor != null) show(editor.focus(ClockField.MINUTES)) else change(-1)
    }

    override fun right() {
        val editor = setClock
        if (editor != null) show(editor.focus(ClockField.SECONDS)) else change(1)
    }

    override fun ok() {
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
            else -> Unit
        }
    }

    override fun back() {
        if (setClock != null) show(null) else close()
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
            MenuRow.GAME_CLOCK, MenuRow.NEW_GAME, null -> Unit
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

    private fun notifyChange() = onChange(
        focused?.let { MenuState(it, pendingGameLengthMs, pendingLanguage ?: language, setClock) },
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
