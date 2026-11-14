package com.casco.kettle.shell.history

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.HistorySessionView
import com.casco.kettle.domain.HistoryStats
import com.casco.kettle.domain.HistoryWeek
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.historySessionsView
import com.casco.kettle.domain.historyStats
import com.casco.kettle.domain.historyWeeks
import com.casco.kettle.domain.recordsAtTheTime
import com.casco.kettle.domain.startOfWeek
import com.casco.kettle.shell.R
import com.casco.kettle.shell.export.logShareRefused
import com.casco.kettle.shell.export.rememberFileShare
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.shell.i18n.firstWeekday
import com.casco.kettle.shell.i18n.formatDayRange
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.NoResults
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.ScrollsToTopOnReselect
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.runner.RunnerHost
import com.casco.kettle.shell.runner.RunnerView
import com.casco.kettle.shell.stats.rememberStatsTime
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What the Progress tab draws in its Sessions section (#411). */
@Composable
fun HistoryRoute() {
    val files = LocalLatestFiles.current
    val share = rememberFileShare()
    val scope = rememberCoroutineScope()
    val runner by RunnerHost.view.collectAsState()
    val navigator = LocalNavigator.current
    val time = rememberStatsTime()
    HistoryScreen(
        files,
        HistoryTime(time.now, time.zone, firstWeekday(LocalConfiguration.current.locales[0])),
        LocalTabReselects.current,
        HistoryActions(
            open = navigator::push,
            export = { id ->
                scope.launch {
                    val sheet = share ?: return@launch
                    logShareRefused(sheet.session(id))
                }
            },
        ),
        runningId = (runner as? RunnerView.Running)?.session?.id,
    )
}

/**
 * When Sessions reads the log from: [now] says which week is "This week", [zone] which day a session
 * started on, and [firstWeekday] where the reader's weeks begin, as Overview's calendar has them.
 */
class HistoryTime(
    val now: Instant,
    val zone: TimeZone,
    val firstWeekday: DayOfWeek,
)

/**
 * What History asks of the shell, as parameters so a test hears each one. [export] shares one session;
 * the whole log is Your file's Export log (#414).
 */
class HistoryActions(
    val open: (Route) -> Unit,
    val export: (id: String) -> Unit,
)

/**
 * Progress's Sessions (#414), which was History (#202): the log as rows, newest first, **grouped by
 * week**, each week titled and totalled, under a search by workout or exercise name. A row carries a
 * PR badge when it set a record at the time, and the entry that did, else its first, under its numbers.
 * Rows expand in place to what each entry put up, with Delete, Edit and Export. While searching, a
 * line over the weeks says how many of the log matched and what they add up to.
 *
 * It reads the log with zero work taken out, as every training view does (`SessionLog.logged`); the
 * whole log's export is Your file's Export log since #414. Native owns deleting a session
 * file; nothing else that writes the log can be on screen with History (#187), so nothing is guarded.
 *
 * [reselected] carries taps on the tab already showing; one on History scrolls back to the top.
 * [runningId] is the session the native runner's service is writing, whose row offers no Delete: one
 * writer while a session runs (#295), and its next set would write the file back anyway.
 */
@Composable
fun HistoryScreen(
    files: LatestFiles,
    time: HistoryTime,
    reselected: Flow<Tab>,
    actions: HistoryActions,
    runningId: String? = null,
) {
    val log = files.sessionLog
    val library = files.library ?: EmptyLibrary
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(Tab.Progress, reselected, listState)

    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val deleting = remember(files) { Deleting(files, scope) }

    val logged = log?.logged.orEmpty()
    val rows = remember(logged, library, time.zone) { historySessionsView(logged, library, time.zone) }
    val records = remember(logged) { recordsAtTheTime(logged) }
    val adHoc = stringResource(R.string.format_adHocSession)
    val listing =
        remember(logged, rows, query, adHoc, time.firstWeekday) {
            Listing(logged, rows, query, adHoc, time.firstWeekday)
        }
    val visible = listing.visible
    val titles = weekTitles(time)

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(start = Spacing.three, end = Spacing.three, top = Spacing.two, bottom = Spacing.six),
        ) {
            // The tab's header names it and switches to Overview (#414). Nothing here until the log is
            // read, so the line never says zero before it has looked.
            if (log == null) return@LazyColumn
            item(key = "summary") {
                listing.matched?.let { SummaryLine(listing.stats, it) }
                if (log.errors.isNotEmpty()) Problems(log.errors)
                SearchBar(query, { query = it }, stringResource(R.string.history_searchPlaceholder))
            }
            for (week in listing.weeks) {
                item(key = "week-${week.start}") { WeekHeader(titles(week.start), listing.statsOf(week)) }
                itemsIndexed(week.rows, key = { _, row -> row.id }) { index, row ->
                    if (index > 0) ListRowSeparator()
                    SessionRow(
                        row,
                        name = listing.nameOf(row),
                        RowState(expandedId == row.id, deleting.failure?.takeIf { it.id == row.id }?.reason),
                        record = records[row.id],
                        rowActions(row, actions, deleting, deletable = row.id != runningId) {
                            expandedId = if (expandedId == row.id) null else row.id
                        },
                    )
                }
            }
            // No first-run half: an empty log is what a new install is, and the line above already
            // reads 0. This covers only the blank body a search makes.
            if (visible.isEmpty() && logged.isNotEmpty()) item(key = "no-results") { NoResults(query) }
        }
    }

    deleting.confirming?.let { row ->
        DeleteDialog(row, name = listing.nameOf(row), onDismiss = { deleting.confirming = null }) {
            deleting.confirm(row)
        }
    }
}

/**
 * What the list shows for [query]: the [rows] whose workout or any of whose exercises matches (#414),
 * filtered but never re-ranked, since this is a timeline and a best-match-first list reads as broken.
 * Matched on the row rather than the session, since only the row has the names ([adHoc] for none).
 * Then the rows by week, each week totalled over what it lists.
 *
 * While searching, the line over the weeks totals what's listed and says how many of the whole it is
 * ([matched]), so a narrowed set is never presented as a lifetime total.
 */
private class Listing(
    logged: List<Session>,
    rows: List<HistorySessionView>,
    query: String,
    adHoc: String,
    firstWeekday: DayOfWeek,
) {
    private val searching = query.isNotBlank()

    /** What a row is called: its workout, or the ad-hoc stand-in, which is what the search matches too. */
    val nameOf: (HistorySessionView) -> String = { it.workoutName ?: adHoc }
    val visible =
        if (searching) {
            rows.filter { row ->
                val names = listOf(nameOf(row)) + row.entries.map { it.exerciseName }
                filterByName(names, query) { it }.isNotEmpty()
            }
        } else {
            rows
        }
    private val byId = logged.associateBy { it.id }
    val stats = historyStats(visible.mapNotNull { byId[it.id] })
    val matched = if (searching) MatchCount(visible.size, logged.size) else null
    val weeks = historyWeeks(visible, firstWeekday)
    private val weekStats = weeks.associate { week -> week.start to historyStats(week.rows.mapNotNull { byId[it.id] }) }

    fun statsOf(week: HistoryWeek): HistoryStats = weekStats.getValue(week.start)
}

/** While searching: [shown] sessions matched, of [total] in the log. */
private data class MatchCount(
    val shown: Int,
    val total: Int,
)

/** What a row's controls do, bound to its session by the screen. [delete] null offers none. */
internal class RowActions(
    val toggle: () -> Unit,
    val delete: (() -> Unit)?,
    val edit: () -> Unit,
    val export: () -> Unit,
)

/** [row]'s controls. A toggle also dismisses a refused delete's message, once its row has been read. */
private fun rowActions(
    row: HistorySessionView,
    actions: HistoryActions,
    deleting: Deleting,
    deletable: Boolean,
    toggle: () -> Unit,
) = RowActions(
    toggle = {
        toggle()
        deleting.dismissFailure(row.id)
    },
    delete = { deleting.confirming = row }.takeIf { deletable },
    edit = { actions.open(Route(Destination.SessionEditor, mapOf("id" to row.id))) },
    export = { actions.export(row.id) },
)

/**
 * A delete from the confirm on: the row being asked about, and the row whose delete the disk refused,
 * with why, which shows in that row, left open.
 */
private class Deleting(
    private val files: LatestFiles,
    private val scope: CoroutineScope,
) {
    var confirming by mutableStateOf<HistorySessionView?>(null)
    var failure by mutableStateOf<DeleteFailure?>(null)
        private set

    /** Closing or reopening the row the refusal shows in is reading it; it doesn't come back. */
    fun dismissFailure(id: String) {
        if (failure?.id == id) failure = null
    }

    fun confirm(row: HistorySessionView) {
        confirming = null
        failure = null
        scope.launch {
            // Not cancelled with the screen: once the file is gone, the log must be read again, or it keeps
            // listing a session that no longer exists.
            withContext(NonCancellable) {
                val refused = writeOrRefusal { files.deleteSession(row.id) }
                failure = refused?.let { DeleteFailure(row.id, it) }
            }
        }
    }
}

/** Session [id]'s delete, refused by the disk for [reason]. */
private data class DeleteFailure(
    val id: String,
    val reason: String,
)

private val EmptyLibrary = Library(1, emptyList(), emptyList(), emptyList())

/**
 * A week's title, "This week" for the one [time] is in, else its days as a range, and "No date" for
 * the rows whose `started_at` isn't an instant.
 */
@Composable
private fun weekTitles(time: HistoryTime): (LocalDate?) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val thisWeek = stringResource(R.string.history_thisWeek)
    val undated = stringResource(R.string.history_undated)
    val current = startOfWeek(time.now.toLocalDateTime(time.zone).date, time.firstWeekday)
    return { start ->
        when (start) {
            null -> undated
            current -> thisWeek
            else -> formatDayRange(start, start.plus(DatePeriod(days = DAYS_IN_WEEK - 1)), locale)
        }
    }
}

/** A week's title and what it adds up to, over a firmer rule than the rows' hairlines. */
@Composable
private fun WeekHeader(
    title: String,
    stats: HistoryStats,
) {
    val colors = KettleTheme.colors
    Row(
        Modifier.padding(top = Spacing.four, bottom = Spacing.one),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = KettleType.heading, color = colors.text)
        Text(
            stringResource(
                R.string.history_weekSummary,
                pluralText(R.plurals.format_session, R.string.format_session_zero, stats.sessions),
                stringResource(R.string.analytics_hoursMinutes, stats.hours, stats.minutes),
            ),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
    ListHeaderRule()
}

private const val DAYS_IN_WEEK = 7

/**
 * While searching, what matched as one sentence: the match count first, so a narrowed set says what it
 * is ("3 of 41") as well as what it adds up to, rather than passing for the whole log. Each count is
 * formatted before it goes in, so it reads "1 session", never "1 sessions".
 */
@Composable
private fun SummaryLine(
    stats: HistoryStats,
    matched: MatchCount,
) {
    val totals =
        stringResource(
            R.string.history_summaryLine,
            pluralText(R.plurals.format_session, R.string.format_session_zero, stats.sessions),
            pluralText(R.plurals.format_set, R.string.format_set_zero, stats.sets),
            // Stats' reading of a time, so the two screens say one total in one form.
            stringResource(R.string.analytics_hoursMinutes, stats.hours, stats.minutes),
        )
    val line = "${stringResource(R.string.history_matchCount, matched.shown, matched.total)} · $totals"
    Text(
        line,
        Modifier.padding(top = 2.dp, bottom = Spacing.two),
        style = KettleType.default,
        color = KettleTheme.colors.textSecondary,
    )
}

/**
 * The session files that couldn't be read, which RN collected and History is where they belong: a
 * session missing from the list is noticed here.
 */
@Composable
private fun Problems(errors: List<String>) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .padding(top = Spacing.two)
            .fillMaxWidth()
            .border(1.dp, colors.accentText, RoundedCornerShape(12.dp))
            .padding(Spacing.two),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            pluralText(R.plurals.history_problemsTitle, R.string.history_problemsTitle_zero, errors.size),
            style = KettleType.smallMedium,
            color = colors.accentText,
        )
        for (error in errors) Text(error, style = KettleType.small, color = colors.textSecondary)
    }
}

@Composable
private fun DeleteDialog(
    row: HistorySessionView,
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (day, month) = dateBadge(row)
    KettleAlertDialog(
        title = stringResource(R.string.history_deleteConfirmTitle),
        body = stringResource(R.string.history_deleteConfirmBody, day, month, name),
        onDismissRequest = onDismiss,
        confirm = DialogAction(stringResource(R.string.common_delete), onConfirm),
        dismiss = DialogAction(stringResource(R.string.common_cancel), onDismiss),
    )
}
