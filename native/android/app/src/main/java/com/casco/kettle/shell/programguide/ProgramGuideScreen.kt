package com.casco.kettle.shell.programguide

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.casco.kettle.shell.R
import com.casco.kettle.shell.rn.JsBridge
import com.casco.kettle.shell.rn.Stacking
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.MaxContentWidth
import com.casco.kettle.shell.theme.Spacing

/**
 * What `shellRoutes` registers at `/program-guide`.
 *
 * Import by `replace`, not `push`: both are sheets, so pushing would leave the reader two deep and drop
 * them back mid-scroll into a guide they're done with. Replacing means dismissing Import lands on
 * Programs, which is where the imported program has appeared.
 */
@Composable
fun ProgramGuideRoute() = ProgramGuideScreen(open = { how, path -> JsBridge.open(how, path) }, onDone = JsBridge::back)

/**
 * The program guide (#199): `src/app/program-guide.tsx`, prose from the locale bundles only. [open] is
 * `JsBridge.open`, a parameter so a test hears how Import was opened, not only that it was.
 */
@Composable
fun ProgramGuideScreen(
    open: (Stacking, String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.three)
                .padding(bottom = Spacing.four)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.programGuide_title),
                Modifier.semantics { heading() },
                style = KettleType.subtitle,
                color = colors.text,
            )
            Body(R.string.programGuide_intro, PaddingValues(top = TitleGap))

            Section(R.string.programGuide_ids_title) { Body(R.string.programGuide_ids_body) }
            Section(R.string.programGuide_write_title) {
                Body(R.string.programGuide_write_body)
                CodeBlock(PROGRAM_SAMPLE)
            }
            Section(R.string.programGuide_fields_title) { Body(R.string.programGuide_fields_body) }
            Section(R.string.programGuide_overrides_title) {
                Body(R.string.programGuide_overrides_intro)
                CodeBlock(OVERRIDES_SAMPLE)
                Body(R.string.programGuide_overrides_body)
            }
            Section(R.string.programGuide_multiDay_title) {
                Body(R.string.programGuide_multiDay_body)
                CodeBlock(MULTI_DAY_SAMPLE)
            }
            Section(R.string.programGuide_restDays_title) {
                Body(R.string.programGuide_restDays_body)
                CodeBlock(REST_DAYS_SAMPLE)
                Body(R.string.programGuide_restDays_tail)
            }
            Section(R.string.programGuide_importIt_title) {
                Body(R.string.programGuide_importIt_body)
                OpenImport { open(Stacking.Replace, "/import") }
            }
            Done(onDone)
        }
    }
}

/**
 * The step this page builds up to, as the control rather than as directions to it. Bordered rather
 * than filled: the filled accent button here is Done, and a second solid one would read as its peer.
 */
@Composable
private fun OpenImport(onClick: () -> Unit) =
    Box(
        Modifier
            .padding(top = Spacing.two)
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .border(1.dp, KettleTheme.colors.border, ButtonShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.three),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.programGuide_openImport),
            style = KettleType.smallMedium,
            color = KettleTheme.colors.text,
        )
    }

@Composable
private fun Done(onClick: () -> Unit) =
    Box(
        Modifier
            .padding(top = Spacing.four)
            .fillMaxWidth()
            // RN's 52px, as a floor: heightIn, never height, which clips at large text sizes.
            .heightIn(min = 52.dp)
            .clip(ButtonShape)
            .background(KettleTheme.colors.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.common_done), style = KettleType.heading, color = KettleTheme.colors.onAccent)
    }

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable ColumnScope.() -> Unit,
) = Column(Modifier.padding(top = SectionGap)) {
    Text(
        stringResource(title),
        Modifier
            .padding(bottom = TitleGap)
            .semantics { heading() },
        style = KettleType.heading,
        color = KettleTheme.colors.text,
    )
    content()
}

/**
 * A paragraph of the guide, with each `<c>` in its string set as code, in place in the sentence. The
 * intro passes its own [padding]; every other paragraph keeps RN's gap below it.
 */
@Composable
private fun Body(
    @StringRes id: Int,
    padding: PaddingValues = PaddingValues(bottom = ParagraphGap),
) = Text(
    codeSpans(stringResource(id)),
    Modifier.padding(padding),
    style = BodyStyle,
    color = KettleTheme.colors.textSecondary,
)

/**
 * [text] with each `<c>…</c>` in it set as code, what `Trans` does with `components={{ c }}`. The
 * tags travel in the string rather than as cut fragments because they are YAML keys sitting
 * mid-sentence, and a language that orders the clause differently has to be able to move them. They
 * don't nest, and a tag left open runs to the end, as `Trans` renders one.
 */
private fun codeSpans(text: String): AnnotatedString =
    buildAnnotatedString {
        var rest = text
        while (rest.isNotEmpty()) {
            val open = rest.indexOf(OPEN)
            if (open < 0) {
                append(rest)
                break
            }
            append(rest.substring(0, open))
            rest = rest.substring(open + OPEN.length)
            val close = rest.indexOf(CLOSE).takeIf { it >= 0 } ?: rest.length
            pushStyle(InlineCode)
            append(rest.substring(0, close))
            pop()
            rest = rest.substring((close + CLOSE.length).coerceAtMost(rest.length))
        }
    }

private const val OPEN = "<c>"
private const val CLOSE = "</c>"

@Composable
private fun CodeBlock(sample: String) {
    val colors = KettleTheme.colors
    Text(
        sample,
        Modifier
            .padding(bottom = ParagraphGap)
            .fillMaxWidth()
            .clip(CodeShape)
            .background(colors.backgroundElement)
            .border(1.dp, colors.border, CodeShape)
            .padding(CodePadding),
        style = CodeStyle,
        color = colors.text,
    )
}

// RN's offsets from the spacing scale, named after what they space: `sectionTitle`, `intro`, `section`,
// `body` and `codeBlock` in program-guide.tsx's styles.
private val TitleGap = Spacing.one + 2.dp
private val SectionGap = Spacing.four - 4.dp
private val ParagraphGap = Spacing.two - 2.dp
private val CodePadding = Spacing.two + 4.dp

private val ButtonShape = RoundedCornerShape(15.dp)
private val CodeShape = RoundedCornerShape(12.dp)

// RN's own sizes for this screen, which sit outside the type scale: `body`, `inline` and `codeText`.
private val BodyStyle = KettleType.default.copy(lineHeight = 20.sp)
private val InlineCode = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
private val CodeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp)

// The samples are the file the reader is about to write, not the page's prose, so they are never
// translated: a localised `rest_day` would be refused on import.
private val PROGRAM_SAMPLE =
    """
    version: 1

    programs:
      - id: pull-progression
        name: 6-Week Pull Progression
        weeks:
          - week: 1
            workout: calisthenics-a
            notes: Baseline — see where you land.
          - week: 3
            workout: calisthenics-a
            overrides:
              - exercise: pullups
                config: { sets: 5 }
          - week: 6
            workout: finisher-circuit
            notes: Deload.
    """.trimIndent()

private val OVERRIDES_SAMPLE =
    """
    overrides:
      - exercise: pullups
        config: { sets: 5 }
      - block: finisher-rounds
        config: { rounds: 2 }
    """.trimIndent()

private val MULTI_DAY_SAMPLE =
    """
    weeks:
      - week: 1
        day: Monday
        workout: push-day
      - week: 1
        day: Thursday
        workout: pull-day
    """.trimIndent()

private val REST_DAYS_SAMPLE =
    """
    weeks:
      - week: 1
        day: Day 1
        workout: push-day
      - week: 1
        day: Day 2
        rest_day: true
        notes: Walk, stretch, nothing heavy.
      - week: 1
        day: Day 3
        workout: pull-day
    """.trimIndent()
