package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.RulesPreset
import net.pladum.tablero.shared.Side
import java.util.Locale

private const val MINUTE_MS = 60_000L

/** How long OK must be held on EXIT to leave for Android TV. */
const val EXIT_HOLD_MS = 2_000L

/**
 * The menu's rows, top to bottom; rows with the same [group] sit together between dividing lines. A [changeable]
 * row's value changes with ←/→ (it shows both chevrons); the others open with OK. A [stoppedOnly] row only opens or
 * changes while the clock is stopped, and is muted while it runs. A [periodRulesOnly] row only shows under period
 * rules (design 32).
 */
enum class MenuRow(
    val group: Int,
    val changeable: Boolean = false,
    val stoppedOnly: Boolean = false,
    val periodRulesOnly: Boolean = false,
) {
    GAME_CLOCK(0, stoppedOnly = true),
    PERIOD(0, changeable = true, periodRulesOnly = true),

    /** The period length under period rules. */
    GAME_LENGTH(0, changeable = true),
    HOME_SCORE(1, changeable = true),
    AWAY_SCORE(1, changeable = true),
    HOME_FOULS(1, changeable = true, periodRulesOnly = true),
    AWAY_FOULS(1, changeable = true, periodRulesOnly = true),
    NEW_GAME(2),
    RULES(2, changeable = true, stoppedOnly = true),
    LAYOUT(2, changeable = true),
    MODE(2, changeable = true),
    LANGUAGE(2, changeable = true),
    HELP(2, stoppedOnly = true),
    EXIT(2, stoppedOnly = true),
    ;

    companion object {
        private val PICKUP_ROWS = entries.filterNot { it.periodRulesOnly }

        /** The rows the menu shows under [rules], top to bottom. */
        fun shownUnder(rules: GameRules): List<MenuRow> = if (rules.periods != null) entries else PICKUP_ROWS
    }
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
    /** Rules chosen with ←/→ and not applied yet, or null to show the engine's. */
    val pendingRules: RulesPreset?,
    /** The board's layout, which the Layout row shows and changes at once. */
    val layout: BoardLayout,
    /** The board's mode, which the Mode row shows and changes at once. */
    val mode: BoardMode,
    /** The language the Language row shows: the one in use, or one chosen and not applied yet. */
    val language: Locale,
    /** The set game clock dialog, shown instead of the menu while open. */
    val setClock: SetClockEditor?,
    /** The exit confirm screen, shown instead of the menu while open. */
    val exitConfirm: ExitConfirm?,
    /** The how to use screen (design 10), shown instead of the menu while open. */
    val help: Boolean,
) {
    /** The rows show: no dialog or screen is open over them. */
    val showsRows: Boolean get() = setClock == null && exitConfirm == null && !help
}

/** Where the how to use screen was opened from, which is where closing it goes back to. */
private enum class HelpOpenedFrom { MENU, LAUNCH }

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
 * The menu (design 06), its set game clock dialog (design 08), its how to use screen (design 10) and its exit confirm
 * screen (design 09). ↑/↓ move between rows, ←/→ change the focused row's value, OK opens the focused row, Back
 * closes the dialog or the menu.
 *
 * Scores, and under period rules fouls and the period, change with every press (one command each, as on the
 * scoreboard). Game length, rules and language are kept pending while their row is focused and applied when focus
 * leaves the row or the menu closes: game length as one [Command.SetGameLength], so stepping through minutes takes a
 * single undo, rules as one [Command.SetRules], and language once, because applying it recreates the activity.
 * Layout and mode apply with every press: the board behind the menu shows them at once. The rows follow the rules in
 * the snapshot ([MenuRow.shownUnder]), so the period and fouls rows appear once period rules apply.
 *
 * Game clock, rules, how to use and exit only open or change while the clock is stopped ([MenuRow.stoppedOnly]): the
 * dialog shows the stopped time, and the two screens hide the board. OK or Back closes how to use; [openHelp] shows it
 * without the menu, on the first launch.
 *
 * Exit (design 09) opens on a confirm screen with CANCEL focused; leaving takes OK held on EXIT for [EXIT_HOLD_MS].
 * Releasing OK sooner, moving the focus or Back cancels the hold.
 *
 * @param layout the board's layout when the menu is built; [applyLayout] gets each change.
 * @param mode the board's mode when the menu is built; [applyMode] gets each change.
 * @param languages the bundled languages, in menu order; [language] is the one in use.
 * @param now the current [TimeSource][net.pladum.tablero.shared.TimeSource] time, which starts a hold.
 * @param postDelayed runs an action after a delay in ms, unless [removeCallbacks] drops it first.
 * @param exitToAndroidTv leaves for the device's home screen; the menu has already closed.
 * @param onChange gets what to show while the menu is open, null when it closes.
 */
class Menu(
    private val dispatch: (Command) -> Unit,
    private val snapshot: () -> GameSnapshot,
    layout: BoardLayout,
    private val applyLayout: (BoardLayout) -> Unit,
    mode: BoardMode,
    private val applyMode: (BoardMode) -> Unit,
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

    private var layout = layout
    private var mode = mode
    private var pendingGameLengthMs: Long? = null
    private var pendingRules: RulesPreset? = null
    private var pendingLanguage: Locale? = null
    private var setClock: SetClockEditor? = null
    private var exitConfirm: ExitConfirm? = null
    private var help: HelpOpenedFrom? = null
    private val finishHold = Runnable { exit() }

    override val isOpen: Boolean get() = focused != null

    override fun open() = open(MenuRow.entries.first())

    /**
     * Opens the menu with [row] focused, e.g. after the activity was recreated with the menu open; the first row when
     * the rules hide [row].
     */
    fun open(row: MenuRow) {
        if (isOpen) return
        focused = row.takeIf { it in shownRows() } ?: MenuRow.entries.first()
        notifyChange()
    }

    /**
     * Shows the how to use screen on its own, e.g. on the first launch, unless the menu is open or the clock runs;
     * closing it closes the menu. Returns whether it opened.
     */
    fun openHelp(): Boolean {
        if (isOpen || snapshot().gameClock.running) return false
        focused = MenuRow.HELP
        help = HelpOpenedFrom.LAUNCH
        notifyChange()
        return true
    }

    /** Applies pending values and closes the menu, dialog included. */
    fun close() {
        if (!isOpen) return
        focused = null
        setClock = null
        exitConfirm = null
        help = null
        removeCallbacks(finishHold)
        applyPending()
        notifyChange()
    }

    /** Applies a pending game length or rules now, e.g. before the activity stops; the menu stays open. */
    fun applyPendingCommands() {
        if (dispatchPending()) notifyChange()
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
            exitConfirm != null || help != null -> Unit
            editor != null -> show(editor.change(1))
            else -> move(-1)
        }
    }

    override fun down() {
        val editor = setClock
        when {
            exitConfirm != null || help != null -> Unit
            editor != null -> show(editor.change(-1))
            else -> move(1)
        }
    }

    override fun left() {
        val editor = setClock
        when {
            help != null -> Unit
            exitConfirm != null -> focusExitChoice(ExitChoice.CANCEL)
            editor != null -> show(editor.focus(ClockField.MINUTES))
            else -> change(-1)
        }
    }

    override fun right() {
        val editor = setClock
        when {
            help != null -> Unit
            exitConfirm != null -> focusExitChoice(ExitChoice.EXIT)
            editor != null -> show(editor.focus(ClockField.SECONDS))
            else -> change(1)
        }
    }

    override fun ok() {
        if (help != null) {
            closeHelp()
            return
        }
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
        val row = focused ?: return
        val s = snapshot()
        if (row.stoppedOnly && s.gameClock.running) return
        when (row) {
            MenuRow.GAME_CLOCK -> show(SetClockEditor.of(s.gameClock.remainingNanos, s.rules))
            MenuRow.NEW_GAME -> {
                dispatch(Command.NewGame)
                close()
            }
            MenuRow.HELP -> {
                help = HelpOpenedFrom.MENU
                notifyChange()
            }
            MenuRow.EXIT -> {
                exitConfirm = ExitConfirm(ExitChoice.CANCEL)
                notifyChange()
            }
            else -> Unit
        }
    }

    override fun okReleased() = cancelExitHold()

    override fun back() {
        when {
            help != null -> closeHelp()
            exitConfirm != null -> closeExitConfirm()
            setClock != null -> show(null)
            else -> close()
        }
    }

    private fun move(delta: Int) {
        val row = focused ?: return
        val rows = shownRows()
        val next = rows.getOrNull(rows.indexOf(row) + delta) ?: return
        focused = next
        applyPending()
        notifyChange()
    }

    private fun change(delta: Int) {
        val row = focused ?: return
        if (row.stoppedOnly && snapshot().gameClock.running) return
        when (row) {
            MenuRow.GAME_LENGTH -> {
                val current = pendingGameLengthMs ?: snapshot().rules.gameLengthMs
                val next = stepGameLength(current, delta)
                if (next == current) return
                pendingGameLengthMs = next
                notifyChange()
            }
            MenuRow.PERIOD -> dispatch(Command.ChangePeriod(delta))
            MenuRow.HOME_SCORE -> dispatch(Command.Score(Side.LEFT, delta))
            MenuRow.AWAY_SCORE -> dispatch(Command.Score(Side.RIGHT, delta))
            MenuRow.HOME_FOULS -> dispatch(Command.Foul(Side.LEFT, delta))
            MenuRow.AWAY_FOULS -> dispatch(Command.Foul(Side.RIGHT, delta))
            MenuRow.RULES -> {
                val applied = RulesPreset.of(snapshot().rules)
                pendingRules = RulesPreset.entries.step(pendingRules ?: applied, delta).takeIf { it != applied }
                notifyChange()
            }
            MenuRow.LAYOUT -> {
                layout = BoardLayout.entries.step(layout, delta)
                applyLayout(layout)
                notifyChange()
            }
            MenuRow.MODE -> {
                mode = BoardMode.entries.step(mode, delta)
                applyMode(mode)
                notifyChange()
            }
            MenuRow.LANGUAGE -> {
                if (languages.size < 2) return
                val current = languages.indexOf(pendingLanguage ?: language)
                pendingLanguage = languages[(current + delta).mod(languages.size)].takeIf { it != language }
                notifyChange()
            }
            MenuRow.GAME_CLOCK, MenuRow.NEW_GAME, MenuRow.HELP, MenuRow.EXIT -> Unit
        }
    }

    /** Pending values only exist while their row is focused, so this runs whenever focus leaves a row. */
    private fun applyPending() {
        dispatchPending()
        val chosen = pendingLanguage ?: return
        pendingLanguage = null
        applyLanguage(chosen)
    }

    /** Dispatches a pending game length or rules; returns whether there was one. */
    private fun dispatchPending(): Boolean {
        val gameLength = pendingGameLengthMs
        val rules = pendingRules
        pendingGameLengthMs = null
        pendingRules = null
        if (gameLength != null && gameLength != snapshot().rules.gameLengthMs) {
            dispatch(Command.SetGameLength(gameLength))
        }
        if (rules != null) dispatch(Command.SetRules(rules))
        return gameLength != null || rules != null
    }

    private fun shownRows(): List<MenuRow> = MenuRow.shownUnder(snapshot().rules)

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

    private fun closeHelp() {
        if (help == HelpOpenedFrom.LAUNCH) {
            close()
            return
        }
        help = null
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
        focused?.let {
            MenuState(
                it,
                pendingGameLengthMs,
                pendingRules,
                layout,
                mode,
                pendingLanguage ?: language,
                setClock,
                exitConfirm,
                help != null,
            )
        },
    )
}

/** The entry [delta] places after [current], wrapping around. */
private fun <T> List<T>.step(current: T, delta: Int): T = this[(indexOf(current) + delta).mod(size)]

/**
 * The game length one ←/→ step away from [currentMs]: the previous or next whole minute, within the allowed range.
 * A length that isn't whole minutes (taken from the clock at a game's first start) steps to the nearest one.
 */
internal fun stepGameLength(currentMs: Long, delta: Int): Long {
    val minutes = if (delta > 0) currentMs / MINUTE_MS + 1 else (currentMs + MINUTE_MS - 1) / MINUTE_MS - 1
    return (minutes * MINUTE_MS).coerceIn(GameRules.MIN_GAME_LENGTH_MS, GameRules.MAX_GAME_LENGTH_MS)
}
