package com.casco.kettle.repochecks

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.parseLibraryYaml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** The repo's root, handed over by Gradle (`repo-checks/build.gradle.kts`). */
internal val repoRoot: File by lazy {
    File(requireNotNull(System.getProperty("kettle.repoRoot")) { "kettle.repoRoot is unset; run this through Gradle" })
}

internal fun repoText(path: String): String = File(repoRoot, path).readText()

/** The shipped seed's English structure: the library a reader importing an example already has. */
internal val seed: Library by lazy {
    when (val result = parseLibraryYaml(repoText("content/seed/library.yaml"))) {
        is ParseResult.Ok -> result.value
        else -> error("the shipped seed does not parse: $result")
    }
}

/** The known equipment's keys, `content/equipment.json`, in order: what the app reads as known items. */
internal val vocabulary: List<String> by lazy {
    Json.parseToJsonElement(repoText("content/equipment.json")).jsonArray.map { it.jsonPrimitive.content }
}

/** Why [text] isn't a library the importer accepts, or null when it is: the refusal names the field. */
internal fun refusal(text: String): String? =
    when (val result = parseLibraryYaml(text)) {
        is ParseResult.Ok -> null
        else -> result.toString()
    }
