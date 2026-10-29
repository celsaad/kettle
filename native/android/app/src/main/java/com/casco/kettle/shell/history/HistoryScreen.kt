package com.casco.kettle.shell.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.HistorySessionView
import com.casco.kettle.domain.HistoryStats
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.filterByName
import com.casco.kettle.domain.historySessionsView
import com.casco.kettle.domain.historyStats
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.i18n.pluralText
import com.casco.kettle.shell.list.ListHeaderRule
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.list.ListRowVerticalPadding
import com.casco.kettle.shell.list.SearchBar
import com.casco.kettle.shell.nav.LocalTabReselects
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing
import com.casco.kettle.shell.theme.card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import java.io.IOException

/** What `shellRoutes` registers at `/history`, the tab. */
@Composable
fun HistoryRoute() {
    HistoryScreen(
        LocalLatestFiles.current,
        TimeZone.currentSystemDefault(),
        LocalTabReselects.current,
        HistoryActions(
            open = JsBridge::open,
            export = JsBridge::export,
            sessionsChanged = { JsBridge.filesChanged(FileKind.SESSIONS) },
        ),
    )
}

/**
 * What History asks of the shell, as parameters so a test hears each one. [export] is a session's id,
 * or null for the whole log. [sessionsChanged] follows a delete, so RN's stores re-list the log (#187).
 */
class HistoryActions(
    val open: (Stacking, String, Map<String, String>) -> Unit,
    val export: (id: String?) -> Unit,
    val sessionsChanged: () -> Unit,
)

/**
 * History (#202), the log as rows, newest first, as RN's `(tabs)/history.tsx`, now the empty stub at
 * this path: a one-line summary where six stat tiles used to be, a name search that narrows the line
 * with the list, and rows that expand in place to what each entry put up, with Delete, Edit and Export.
 *
 * It reads the log with zero work taken out, as every training view does (`SessionLog.logged`), and
 * hands the raw one to Export all, since that is the user's own files. Native owns deleting a session
 * file; nothing else that writes the log can be on screen with History (#187), so nothing is guarded.
 *
 * [reselected] carries taps on the tab already showing; one on History scrolls back to the top.
 */
@Composable
fun HistoryScreen(
    files: LatestFiles,
    zone: TimeZone,
    reselected: Flow<Tab>,
    actions: HistoryActions,
) {
    val log = files.sessionLog
    val library = files.library ?: EmptyLibrary
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(reselected, listState)

    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val deleting = remember(files) { Deleting(files, scope, actions.sessionsChanged) }

    val logged = log?.logged.orEmpty()
    val rows = remember(logged, library, zone) { historySessionsView(logged, library, zone) }
    val adHoc = stringResource(R.string.format_adHocSession)
    val listing = remember(logged, rows, query, adHoc) { Listing(logged, rows, query, adHoc) }
    val visible = listing.visible

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            state = listState,
            contentPadding =
                PaddingValues(start = Spacing.three, end = Spacing.three, top = Spacing.two, bottom = Spacing.six),
        ) {
            item(key = "header") {
                Header(
                    showExportAll = log?.sessions?.isNotEmpty() == true,
                    onStats = { actions.open(Stacking.Push, "/analytics", emptyMap()) },
                    onExportAll = { actions.export(null) },
                )
            }
            // Nothing more until the log is read, so the line never says zero before it has looked.
            if (log == null) return@LazyColumn
            item(key = "summary") {
                SummaryLine(listing.stats, listing.matched)
                if (log.errors.isNotEmpty()) Problems(log.errors)
                SearchBar(query, { query = it }, stringResource(R.string.history_searchPlaceholder))
                ListHeaderRule()
            }
            itemsIndexed(visible, key = { _, row -> row.id }) { index, row ->
                if (index > 0) ListRowSeparator()
                SessionRow(
                    row,
                    name = row.workoutName ?: adHoc,
                    expanded = expandedId == row.id,
                    failure = deleting.failure?.takeIf { it.id == row.id }?.reason,
                    RowActions(
                        toggle = { expandedId = if (expandedId == row.id) null else row.id },
                        delete = { deleting.confirming = row },
                        edit = { actions.open(Stacking.Push, "/session-editor", mapOf("id" to row.id)) },
                        export = { actions.export(row.id) },
                    ),
                )
            }
            // No first-run half: an empty log is what a new install is, and the line above already
            // reads 0. This covers only the blank body a search makes.
            if (visible.isEmpty() && logged.isNotEmpty()) item(key = "no-results") { NoResults(query) }
        }
    }

    deleting.confirming?.let { row ->
        DeleteDialog(row, name = row.workoutName ?: adHoc, onDismiss = { deleting.confirming = null }) {
            deleting.confirm(row)
        }
    }
}

/** Re-tap-to-top, which #153 dropped with RN's bar and each tab's port brings back for its own list. */
@Composable
private fun ScrollsToTopOnReselect(
    reselected: Flow<Tab>,
    listState: LazyListState,
) = LaunchedEffect(reselected, listState) {
    reselected.filter { it == Tab.History }.collect { listState.animateScrollToItem(0) }
}

/**
 * What the list shows for [query]: the [rows] whose workout matches, filtered but never re-ranked,
 * since this is a timeline and a best-match-first History reads as broken. Matched on the row rather
 * than the session, since only the row has the workout's name ([adHoc] for none).
 *
 * The summary totals what's listed, not the whole archive, and while searching says how many of the
 * whole it is ([matched]), so a narrowed set is never presented as a lifetime total.
 */
private class Listing(
    logged: List<Session>,
    rows: List<HistorySessionView>,
    query: String,
    adHoc: String,
) {
    private val searching = query.isNotBlank()
    val visible = if (searching) filterByName(rows, query) { it.workoutName ?: adHoc } else rows
    private val ids = visible.mapTo(HashSet()) { it.id }
    val stats = historyStats(if (searching) logged.filter { it.id in ids } else logged)
    val matched = if (searching) visible.size to logged.size else null
}

/** What a row's controls do, bound to its session by the screen. */
internal class RowActions(
    val toggle: () -> Unit,
    val delete: () -> Unit,
    val edit: () -> Unit,
    val export: () -> Unit,
)

/**
 * A delete from the confirm on: the row being asked about, and the row whose delete the disk refused,
 * with why, which shows in that row, left open.
 */
private class Deleting(
    private val files: LatestFiles,
    private val scope: CoroutineScope,
    private val sessionsChanged: () -> Unit,
) {
    var confirming by mutableStateOf<HistorySessionView?>(null)
    var failure by mutableStateOf<DeleteFailure?>(null)
        private set

    fun confirm(row: HistorySessionView) {
        confirming = null
        failure = null
        scope.launch {
            // Not cancelled with the screen: once the file is gone, RN's stores must hear it, or they
            // keep listing a session that no longer exists.
            withContext(NonCancellable) {
                try {
                    files.deleteSession(row.id)
                    sessionsChanged()
                } catch (e: IOException) {
                    failure = DeleteFailure(row.id, e.message.orEmpty())
                } catch (e: SecurityException) {
                    failure = DeleteFailure(row.id, e.message.orEmpty())
                }
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

@Composable
private fun Header(
    showExportAll: Boolean,
    onStats: () -> Unit,
    onExportAll: () -> Unit,
) {
    val colors = KettleTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.history_title),
            Modifier.weight(1f).semantics { heading() },
            style = KettleType.subtitle,
            color = colors.text,
        )
        // Always offered, even on an empty log: arriving at zeros explains what Stats is, where a link
        // that appears only once there is history is one nobody learns about.
        HeaderLink(stringResource(R.string.history_stats), onStats)
        // Hidden at zero, since there's nothing to export. "Export all" rather than "Export": each open
        // row has its own Export for one session, and two identical labels meaning different amounts of
        // data is found out only after sharing the wrong one.
        if (showExportAll) HeaderLink(stringResource(R.string.history_exportAll), onExportAll)
    }
}

/** A text action: its name is the words on screen, and its 48dp target grows with the text. */
@Composable
private fun HeaderLink(
    text: String,
    onClick: () -> Unit,
) = Text(
    text,
    Modifier
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = Spacing.two, vertical = Spacing.three - 2.dp),
    style = KettleType.smallMedium,
    color = KettleTheme.colors.accentText,
)

/**
 * The whole header's numbers as one sentence, where six stat cards used to fill the first screen.
 * While searching it leads with the match count, so a narrowed set says what it is ("3 of 41") as
 * well as what it adds up to, rather than quietly relabelling a subset. Each count is formatted
 * before it goes in, so the first week reads "1 session", never "1 sessions".
 */
@Composable
private fun SummaryLine(
    stats: HistoryStats,
    matched: Pair<Int, Int>?,
) {
    val totals =
        stringResource(
            R.string.history_summaryLine,
            pluralText(R.plurals.format_session, R.string.format_session_zero, stats.sessions),
            pluralText(R.plurals.format_set, R.string.format_set_zero, stats.sets),
            // Stats' reading of a time, so the two screens say one total in one form.
            stringResource(R.string.analytics_hoursMinutes, stats.hours, stats.minutes),
        )
    val line =
        matched?.let { (shown, total) -> "${stringResource(R.string.history_matchCount, shown, total)} · $totals" }
            ?: totals
    Text(line, Modifier.padding(top = 2.dp), style = KettleType.default, color = KettleTheme.colors.textSecondary)
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

/**
 * What a search that matched nothing shows, as RN's `NoResults`: a blank body under intact chrome
 * reads as a bug, not an answer.
 */
@Composable
private fun NoResults(query: String) {
    val colors = KettleTheme.colors
    Column(
        Modifier
            .padding(top = Spacing.three)
            .fillMaxWidth()
            .card(16.dp)
            .padding(Spacing.three),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.search_noMatchTitle), style = KettleType.heading, color = colors.text)
        Text(
            stringResource(R.string.search_noMatchBody, query.trim()),
            style = KettleType.small,
            color = colors.textSecondary,
        )
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
    val colors = KettleTheme.colors
    val buttons = ButtonDefaults.textButtonColors(contentColor = colors.accentText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_deleteConfirmTitle)) },
        text = {
            Text(
                stringResource(
                    R.string.history_deleteConfirmBody,
                    day,
                    month,
                    name,
                ),
            )
        },
        confirmButton = { TextButton(onConfirm, colors = buttons) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onDismiss, colors = buttons) { Text(stringResource(R.string.common_cancel)) } },
        // The app's surfaces rather than Material's defaults, which aren't Kettle's palette and were
        // never contrast-checked against it; accentText on backgroundElement is RN's checked pairing.
        containerColor = colors.backgroundElement,
        titleContentColor = colors.text,
        textContentColor = colors.textSecondary,
    )
}
