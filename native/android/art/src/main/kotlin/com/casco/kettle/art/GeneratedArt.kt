package com.casco.kettle.art

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory

/** The generated resources: drawable XML by resource name, and the Kotlin source of the id lookup. */
class GeneratedArt(
    val drawables: Map<String, String>,
    val lookup: String,
)

/**
 * `art.json` and the drawings it names (file name without `.svg` → markup), as VectorDrawables named
 * `art_<drawing>` and a lookup from exercise id to each one's `R.drawable`.
 *
 * Only the subset `src/content/art/README.md` names is accepted, and anything else fails the build
 * with the file and the offender in the message. A converter that skipped what it didn't understand
 * would ship a drawing with a stroke missing, which nothing downstream would notice.
 */
fun generateArt(
    artJson: String,
    svgs: Map<String, String>,
): GeneratedArt {
    val drawings =
        Json
            .parseToJsonElement(artJson)
            .jsonObject
            .getValue("drawings")
            .jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    // Both directions, since each fails silently on its own: an id with no file draws nothing, and a
    // file nothing names is dead weight in the APK.
    drawings.entries.firstOrNull { it.value !in svgs }?.let { (id, drawing) ->
        throw IllegalArgumentException("art.json draws $id with src/content/art/$drawing.svg, which doesn't exist")
    }
    (svgs.keys - drawings.values.toSet()).firstOrNull()?.let { drawing ->
        throw IllegalArgumentException("src/content/art/$drawing.svg: no id in art.json draws with it")
    }
    val drawables = svgs.entries.associate { (name, svg) -> resourceName(name) to Drawing(name).vector(svg) }
    return GeneratedArt(drawables, lookup(drawings))
}

private fun resourceName(drawing: String) = "art_" + drawing.replace('-', '_')

/** Where the generated lookup lives in `:app`, as its package and as the path under a source root. */
const val OUTPUT_PACKAGE = "com.casco.kettle.shell.art"

private fun lookup(drawings: Map<String, String>) =
    buildString {
        appendLine("// Generated from src/content/art.json by :art. Edit art.json, not this file.")
        appendLine("package $OUTPUT_PACKAGE")
        appendLine()
        appendLine("import com.casco.kettle.shell.R")
        appendLine()
        appendLine("/** Each exercise id with a drawing, and the drawable :art generated for it. */")
        appendLine("internal val artDrawables: Map<String, Int> =")
        appendLine("    mapOf(")
        for ((id, drawing) in drawings) appendLine("        \"$id\" to R.drawable.${resourceName(drawing)},")
        appendLine("    )")
        appendLine()
        appendLine("/**")
        appendLine(" * Each exercise id with a drawing, and the string describing its movement: `exerciseArt.<id>`")
        appendLine(" * in the locale bundles, by the resource name :strings gives that key.")
        appendLine(" */")
        appendLine("internal val artDescriptions: Map<String, Int> =")
        appendLine("    mapOf(")
        // :strings' naming, so a drawing whose description is missing fails to compile rather than
        // drawing unannounced. The bundles' coverage of art.json is jest's to hold.
        for (id in drawings.keys) appendLine("        \"$id\" to R.string.exerciseArt_${id.replace('-', '_')},")
        appendLine("    )")
    }

/** Opaque, and replaced wholesale by whatever tint draws the drawable, the way an icon's colour is. */
private const val PLACEHOLDER_COLOR = "#FF000000"

private const val STROKE_COLOR = "currentColor"

private const val VIEW_BOX_NUMBERS = 4

private val NUMBER = Regex("""-?(\d+\.?\d*|\.\d+)""")

/** SVG path syntax as a vector's `pathData` reads it: commands, numbers, separators, nothing else. */
private val PATH_DATA = Regex("""[MmLlHhVvCcSsQqTtAaZz\d.,\s+eE-]+""")

private val TRANSFORM = Regex("""(translate|rotate|scale)\(([^)]*)\)""")

/** The operations a `<group>` can express, in the only order it applies them. */
private val TRANSFORM_ORDER = listOf("translate", "rotate", "scale")

/** How many numbers each operation takes; `rotate`'s three-number form has no `<group>` equivalent. */
private val TRANSFORM_ARITY = mapOf("translate" to 1..2, "rotate" to 1..1, "scale" to 1..2)

private class Drawing(
    val name: String,
) {
    fun vector(svg: String): String {
        val root = parse(svg)
        expectAttributes(root, required = setOf("viewBox", "fill"), optional = setOf("xmlns"))
        expect(root.tagName == "svg") { "the root is <${root.tagName}>, not <svg>" }
        expect(root.getAttribute("fill") == "none") { "<svg> has fill=\"${root.getAttribute("fill")}\"; only \"none\"" }
        val viewBox = root.getAttribute("viewBox").trim().split(Regex("""\s+"""))
        expect(
            viewBox.size == VIEW_BOX_NUMBERS && viewBox.all(NUMBER::matches) && viewBox.take(2) == listOf("0", "0"),
        ) {
            "viewBox \"${root.getAttribute("viewBox")}\" has to be \"0 0 <width> <height>\""
        }
        val (width, height) = viewBox.drop(2)

        val canvas =
            root.elementChildren().singleOrNull()?.takeIf { it.tagName == "g" }
                ?: fail("<svg> has to hold exactly one root <g>")
        expectAttributes(canvas, required = setOf("stroke", "stroke-width", "stroke-linecap", "stroke-linejoin"))
        expect(canvas.getAttribute("stroke") == STROKE_COLOR) {
            "the root <g> strokes in \"${canvas.getAttribute(
                "stroke",
            )}\"; only \"$STROKE_COLOR\", which the tint replaces"
        }
        expect(NUMBER.matches(canvas.getAttribute("stroke-width"))) { "stroke-width has to be a number" }
        expect(canvas.getAttribute("stroke-linecap") in setOf("butt", "round", "square")) {
            "stroke-linecap is not one Android has"
        }
        expect(canvas.getAttribute("stroke-linejoin") in setOf("miter", "round", "bevel")) {
            "stroke-linejoin is not one Android has"
        }
        val stroke =
            listOf(
                "android:strokeColor=\"$PLACEHOLDER_COLOR\"",
                "android:strokeWidth=\"${canvas.getAttribute("stroke-width")}\"",
                "android:strokeLineCap=\"${canvas.getAttribute("stroke-linecap")}\"",
                "android:strokeLineJoin=\"${canvas.getAttribute("stroke-linejoin")}\"",
            )

        return buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("<!-- Generated from src/content/art/$name.svg by :art. Edit the SVG, not this file. -->")
            appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    android:width="${width}dp"""")
            appendLine("""    android:height="${height}dp"""")
            appendLine("""    android:viewportWidth="$width"""")
            appendLine("""    android:viewportHeight="$height">""")
            for (element in canvas.elementChildren()) appendElement(element, stroke, "    ")
            appendLine("</vector>")
        }
    }

    private fun StringBuilder.appendElement(
        element: Element,
        stroke: List<String>,
        indent: String,
    ) {
        val pathData =
            when (element.tagName) {
                "g" -> {
                    expectAttributes(element, required = setOf("transform"))
                    appendTag(indent, "group", groupAttributes(element.getAttribute("transform")), close = false)
                    for (child in element.elementChildren()) appendElement(child, stroke, "$indent    ")
                    appendLine("$indent</group>")
                    return
                }

                "path" -> {
                    expectAttributes(element, required = setOf("d"))
                    element
                        .getAttribute(
                            "d",
                        ).also { expect(PATH_DATA.matches(it)) { "<path> d=\"$it\" is not path data" } }
                }

                "circle" -> {
                    expectAttributes(element, required = setOf("cx", "cy", "r"))
                    circle(element)
                }

                else -> {
                    fail("<${element.tagName}> is outside the subset: only <path>, <circle> and <g transform>")
                }
            }
        appendTag(indent, "path", listOf("android:pathData=\"$pathData\"") + stroke, close = true)
    }

    private fun StringBuilder.appendTag(
        indent: String,
        tag: String,
        attributes: List<String>,
        close: Boolean,
    ) {
        appendLine("$indent<$tag")
        attributes.forEachIndexed { i, attribute ->
            val end =
                if (i < attributes.lastIndex) {
                    ""
                } else if (close) {
                    " />"
                } else {
                    ">"
                }
            appendLine("$indent    $attribute$end")
        }
    }

    /**
     * `translate(x y) rotate(deg) scale(s)` as a `<group>`'s attributes, each part optional. A group
     * scales, then rotates, then translates, which is exactly what that SVG list means read right to
     * left; any other order would need nested groups, so it is refused rather than guessed at.
     */
    private fun groupAttributes(transform: String): List<String> {
        val operations = TRANSFORM.findAll(transform).toList()
        val refused = { reason: String -> fail("transform=\"$transform\": $reason") }
        if (TRANSFORM.replace(transform, "").isNotBlank()) refused("only translate, rotate and scale")
        val names = operations.map { it.groupValues[1] }
        if (names != TRANSFORM_ORDER.filter { it in names } || names.toSet().size != names.size) {
            refused("translate, rotate and scale, each at most once and in that order")
        }
        return operations
            .flatMap { match ->
                val (operation, arguments) = match.destructured
                val values = arguments.trim().split(Regex("""[\s,]+"""))
                if (values.size !in TRANSFORM_ARITY.getValue(operation) || !values.all(NUMBER::matches)) {
                    refused("$operation takes ${TRANSFORM_ARITY.getValue(operation)} numbers")
                }
                when (operation) {
                    "translate" -> listOf("translateX" to values[0], "translateY" to values.getOrElse(1) { "0" })
                    "rotate" -> listOf("rotation" to values[0])
                    else -> listOf("scaleX" to values[0], "scaleY" to values.getOrElse(1) { values[0] })
                }
            }.map { (attribute, value) -> "android:$attribute=\"$value\"" }
    }

    /** Two half arcs from the leftmost point round and back. Decimal, so `72 - 7.5` is `64.5` and no more. */
    private fun circle(element: Element): String {
        val (cx, cy, r) =
            listOf("cx", "cy", "r").map {
                val value = element.getAttribute(it)
                expect(NUMBER.matches(value)) { "<circle> $it=\"$value\" is not a number" }
                BigDecimal(value)
            }
        val left = (cx - r).plain()
        val right = (cx + r).plain()
        val radius = r.plain()
        val y = cy.plain()
        return "M$left,$y A$radius,$radius 0 1 0 $right,$y A$radius,$radius 0 1 0 $left,$y Z"
    }

    private fun expectAttributes(
        element: Element,
        required: Set<String>,
        optional: Set<String> = emptySet(),
    ) {
        val present = (0 until element.attributes.length).map { element.attributes.item(it).nodeName }.toSet()
        val tag = "<${element.tagName}>"
        (present - required - optional).firstOrNull()?.let { fail("$tag takes no $it attribute") }
        (required - present).firstOrNull()?.let { fail("$tag needs a $it attribute") }
    }

    /** Elements only, with comments and whitespace skipped. Text anywhere else is refused. */
    private fun Element.elementChildren(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.mapNotNull { node ->
            when {
                node.nodeType == Node.ELEMENT_NODE -> node as Element
                node.nodeType == Node.COMMENT_NODE -> null
                node.nodeType == Node.TEXT_NODE && node.textContent.isBlank() -> null
                else -> fail("<$tagName> holds text \"${node.textContent.trim()}\"")
            }
        }

    /** `require`, but naming the file, which is the first thing a failed build needs to say. */
    private fun expect(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) fail(message())
    }

    fun fail(message: String): Nothing = throw IllegalArgumentException("src/content/art/$name.svg: $message")

    private fun parse(svg: String): Element =
        DocumentBuilderFactory
            .newInstance()
            .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))
            .documentElement
}

private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
