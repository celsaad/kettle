package spike

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import spike.res.Res
import spike.res.greeting
import spike.res.sessions
import spike.res.sessions_with_zero_item
import spike.res.sessions_zero

// Each test renders one line per lookup and asserts the exact text, so a wrong locale or plural
// category fails on the line that names it.
@RunWith(RobolectricTestRunner::class)
abstract class Base {
    @get:Rule val rule = createComposeRule()

    fun render(vararg lines: @androidx.compose.runtime.Composable () -> String) {
        rule.setContent {
            androidx.compose.foundation.layout.Column { lines.forEach { BasicText(it()) } }
        }
    }

    fun shows(vararg texts: String) = texts.forEach { rule.onNodeWithText(it).assertExists() }
}

class EnglishTest : Base() {
    @Test fun english() {
        render(
            { stringResource(Res.string.greeting) },
            { pluralStringResource(Res.plurals.sessions, 1, 1) },
            { pluralText(Res.plurals.sessions, Res.string.sessions_zero, 0) },
            { pluralStringResource(Res.plurals.sessions_with_zero_item, 0, 0) },
        )
        shows("Hello", "1 session", "0 sessions", "OTHER 0")
    }
}

@Config(qualifiers = "pt")
class PortugueseTest : Base() {
    @Test fun qualifierPicksPortuguese() {
        render({ stringResource(Res.string.greeting) })
        shows("Olá")
    }

    @Test fun pluralRule() {
        render(
            { pluralStringResource(Res.plurals.sessions, 1, 1) },
            { pluralStringResource(Res.plurals.sessions, 2, 2) },
        )
        shows("1 sessão", "2 sessões")
    }

    /** The bug `:strings` guards against: CLDR files Portuguese 0 under `one`. */
    @Test fun zeroWithoutTheBranchIsTheBug() {
        render({ pluralStringResource(Res.plurals.sessions, 0, 0) })
        shows("0 sessão")
    }

    @Test fun zeroWithTheBranch() {
        render({ pluralText(Res.plurals.sessions, Res.string.sessions_zero, 0) })
        shows("0 sessões")
    }

    @Test fun explicitZeroItemIsNeverSelected() {
        render({ pluralStringResource(Res.plurals.sessions_with_zero_item, 0, 0) })
        shows("UM 0")
    }
}

@Config(qualifiers = "ja")
class JapaneseTest : Base() {
    @Test fun japanese() {
        render(
            { stringResource(Res.string.greeting) },
            { pluralStringResource(Res.plurals.sessions, 1, 1) },
            { pluralText(Res.plurals.sessions, Res.string.sessions_zero, 0) },
        )
        shows("こんにちは", "1 回", "0 回")
    }
}
