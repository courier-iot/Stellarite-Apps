package com.stellarite.apps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stellarite.apps.ui.theme.SA

/** A small Markdown subset, enough for typical GitHub READMEs. Images and raw HTML are dropped. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Item(val marker: String, val indent: Int, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    /** rows[0] is the header row. Every row has exactly aligns.size cells. */
    data class Table(val rows: List<List<String>>, val aligns: List<MdAlign>) : MdBlock
    data object Rule : MdBlock
}

/** Column alignment from the `|:---|:---:|---:|` line. None means the GitHub default. */
enum class MdAlign { None, Start, Center, End }

private val headingRe = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val itemRe = Regex("""^(\s*)([-*+]|\d+[.)])\s+(.*)$""")
private val quoteRe = Regex("""^>\s?(.*)$""")
private val ruleRe = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
private val tableSepRe = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")
private val htmlComment = Regex("""<!--[\s\S]*?-->""")
private val htmlBr = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val htmlTag = Regex("""</?[a-zA-Z][^>]*>""")
private val imageRe = Regex("""!\[[^\]]*]\([^)]*\)""")
private val emptyLinkRe = Regex("""\[\s*]\([^)]*\)""")

fun parseMarkdown(source: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = htmlComment.replace(source.replace("\r\n", "\n"), "").lines()
    val para = StringBuilder()
    fun flush() {
        val t = clean(para.toString())
        if (t.isNotBlank()) out += MdBlock.Para(t)
        para.clear()
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        when {
            t.startsWith("```") || t.startsWith("~~~") -> {
                flush()
                val fence = t.take(3)
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    code.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(code.toString().trimEnd())
            }
            t.isEmpty() -> flush()
            headingRe.matches(t) -> {
                flush()
                val m = headingRe.find(t)!!
                val text = clean(m.groupValues[2])
                if (text.isNotBlank()) out += MdBlock.Heading(m.groupValues[1].length, text)
            }
            ruleRe.matches(t) -> {
                flush()
                out += MdBlock.Rule
            }
            quoteRe.matches(t) -> {
                flush()
                val text = clean(quoteRe.find(t)!!.groupValues[1])
                if (text.isNotBlank()) out += MdBlock.Quote(text)
            }
            t.contains('|') && i + 1 < lines.size && tableSepRe.matches(lines[i + 1].trim()) -> {
                flush()
                val header = splitRow(t)
                val aligns = splitRow(lines[i + 1].trim()).map { c ->
                    when {
                        c.startsWith(":") && c.endsWith(":") -> MdAlign.Center
                        c.endsWith(":") -> MdAlign.End
                        c.startsWith(":") -> MdAlign.Start
                        else -> MdAlign.None
                    }
                }.take(header.size).let { it + List(header.size - it.size) { MdAlign.None } }
                val rows = mutableListOf(header)
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    // GitHub rule: extra cells are dropped, missing cells are empty.
                    val r = splitRow(lines[i].trim()).take(header.size)
                    rows += r + List(header.size - r.size) { "" }
                    i++
                }
                i-- // the loop increments again
                out += MdBlock.Table(rows, aligns)
            }
            itemRe.matches(raw) -> {
                flush()
                val m = itemRe.find(raw)!!
                val text = clean(m.groupValues[3])
                if (text.isNotBlank()) {
                    val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                    out += MdBlock.Item(marker, m.groupValues[1].length / 2, text)
                }
            }
            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(t)
            }
        }
        i++
    }
    flush()
    return out
}

/** Splits a table line on '|', keeping escaped \| and pipes inside `code` as text. */
private fun splitRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.substring(1)
    if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
    val cells = mutableListOf<String>()
    val cur = StringBuilder()
    var inCode = false
    var k = 0
    while (k < t.length) {
        val ch = t[k]
        when {
            ch == '\\' && k + 1 < t.length && t[k + 1] == '|' -> { cur.append('|'); k++ }
            ch == '`' -> { inCode = !inCode; cur.append(ch) }
            ch == '|' && !inCode -> { cells += clean(cur.toString().trim()); cur.clear() }
            else -> cur.append(ch)
        }
        k++
    }
    cells += clean(cur.toString().trim())
    return cells
}

/** Drops images, empty links and HTML tags from a line of prose. */
private fun clean(s: String): String {
    var t = imageRe.replace(s, "")
    t = emptyLinkRe.replace(t, "")
    t = htmlBr.replace(t, " ")
    t = htmlTag.replace(t, "")
    return t.trim()
}

private fun resolveUrl(u: String, repoUrl: String): String? = when {
    u.startsWith("#") -> null
    u.startsWith("http://") || u.startsWith("https://") || u.startsWith("mailto:") -> u
    else -> "$repoUrl/blob/HEAD/${u.removePrefix("./").removePrefix("/")}"
}

private val inlineRe = Regex(
    """(?<code>`+)(?<ctext>.+?)\k<code>""" +
        """|(?<link>\[(?<ltext>[^\]]+)]\((?<lurl>[^)\s]+)(?:\s+"[^"]*")?\))""" +
        """|\*\*(?<b1>.+?)\*\*|__(?<b2>.+?)__""" +
        """|(?<![\w*])\*(?<i1>[^*\s][^*]*?)\*(?![\w*])|(?<![\w])_(?<i2>[^_\s][^_]*?)_(?![\w])""" +
        """|(?<url>https?://[^\s)<>]+)"""
)

private fun inline(text: String, repoUrl: String, link: Color, codeBg: Color, codeFg: Color): AnnotatedString =
    buildAnnotatedString {
        var idx = 0
        for (m in inlineRe.findAll(text)) {
            append(text.substring(idx, m.range.first))
            val g = m.groups
            when {
                g["ctext"] != null ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeFg)) {
                        append(g["ctext"]!!.value)
                    }
                g["link"] != null -> {
                    val label = g["ltext"]!!.value
                    val url = resolveUrl(g["lurl"]!!.value, repoUrl)
                    if (url == null) append(label) else linkSpan(label, url, link)
                }
                g["b1"] != null || g["b2"] != null ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append((g["b1"] ?: g["b2"])!!.value) }
                g["i1"] != null || g["i2"] != null ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append((g["i1"] ?: g["i2"])!!.value) }
                g["url"] != null -> linkSpan(g["url"]!!.value, g["url"]!!.value, link)
            }
            idx = m.range.last + 1
        }
        append(text.substring(idx))
    }

private fun AnnotatedString.Builder.linkSpan(label: String, url: String, color: Color) {
    withLink(
        LinkAnnotation.Url(
            url,
            TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
        )
    ) { append(label) }
}

@Composable
fun MarkdownView(source: String, repoUrl: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseMarkdown(source) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { Block(it, repoUrl) }
    }
}

@Composable
private fun rich(text: String, repoUrl: String): AnnotatedString =
    remember(text, repoUrl, SA.theme) {
        inline(text, repoUrl, SA.Accent, SA.Void, SA.Text)
    }

@Composable
private fun Block(b: MdBlock, repoUrl: String) {
    when (b) {
        is MdBlock.Heading -> Text(
            rich(b.text, repoUrl),
            color = SA.Text,
            fontSize = when (b.level) { 1 -> 20.sp; 2 -> 18.sp; 3 -> 16.sp; else -> 15.sp },
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = if (b.level <= 2) 4.dp else 2.dp),
        )
        is MdBlock.Para -> Text(rich(b.text, repoUrl), color = SA.Text, fontSize = 14.sp, lineHeight = 20.sp)
        is MdBlock.Item -> Row(Modifier.padding(start = (b.indent * 14).dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(b.marker, color = SA.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            Text(rich(b.text, repoUrl), color = SA.Text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        }
        is MdBlock.Code -> Box(
            Modifier
                .fillMaxWidth()
                .background(SA.Void)
                .border(1.dp, SA.Line2)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(b.text, color = SA.Text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp))
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(SA.Line3))
            Text(
                rich(b.text, repoUrl),
                color = SA.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        is MdBlock.Table -> MdTable(b, repoUrl)
        MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(SA.Line))
    }
}


private val linkTextRe = Regex("""\[([^\]]*)]\([^)]*\)""")

/** Rough visible length of a cell: link targets and *, _, ` markers don't take up space. */
private fun visibleLen(s: String): Int =
    linkTextRe.replace(s, "$1").replace("*", "").replace("_", "").replace("`", "").length

private const val CHAR_DP = 8f   // average width of one 14sp character
private const val CELL_PAD = 10  // horizontal padding on each side of a cell, in dp

/**
 * A GitHub-style table: bordered cells, text wrapping inside each cell, a bold header row
 * and striped body rows. Columns share the screen width in proportion to their content;
 * if even the narrowest sensible layout is wider than the screen, the table scrolls sideways.
 */
@Composable
private fun MdTable(b: MdBlock.Table, repoUrl: String) {
    val cols = b.aligns.size
    if (cols == 0) return
    // Per column: the longest single word sets the narrowest width, the longest cell sets its share.
    val stats = remember(b) {
        List(cols) { c ->
            val cells = b.rows.map { it[c] }
            val word = cells.maxOf { cell -> cell.split(' ').maxOfOrNull { visibleLen(it) } ?: 0 }
            val whole = cells.maxOf { visibleLen(it) }
            word to whole
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth
        val minW = stats.map { (word, _) -> (word * CHAR_DP + 2 * CELL_PAD).coerceIn(48f, 170f).dp }
        val share = stats.map { (_, whole) -> whole.coerceIn(3, 40).toFloat() }
        val minSum = minW.fold(0.dp) { a, w -> a + w }
        val scroll = minSum > avail

        val widths: List<Dp> = if (scroll) {
            // Too wide for the screen: give each column a comfortable fixed width and scroll.
            List(cols) { c -> maxOf(minW[c], (share[c] * CHAR_DP * 0.6f + 2 * CELL_PAD).coerceAtMost(240f).dp) }
        } else {
            // Fill the screen by share, but never below a column's minimum.
            val w = MutableList(cols) { 0.dp }
            val fixed = BooleanArray(cols)
            repeat(cols) {
                val left = avail - (0 until cols).filter { fixed[it] }.fold(0.dp) { a, c -> a + w[c] }
                val total = (0 until cols).filter { !fixed[it] }.sumOf { share[it].toDouble() }.toFloat()
                var changed = false
                for (c in 0 until cols) if (!fixed[c]) {
                    w[c] = left * (share[c] / total)
                    if (w[c] < minW[c]) { w[c] = minW[c]; fixed[c] = true; changed = true }
                }
                if (!changed) return@repeat
            }
            w
        }

        Box(Modifier.fillMaxWidth().then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier)) {
            Column(Modifier.border(0.5.dp, SA.Line2)) {
                b.rows.forEachIndexed { r, row ->
                    val header = r == 0
                    val striped = !header && r % 2 == 0 // 2nd, 4th… body rows, like GitHub
                    Row(
                        Modifier
                            .height(IntrinsicSize.Min)
                            .background(if (striped) SA.PanelRaised else Color.Transparent)
                    ) {
                        row.forEachIndexed { c, cell ->
                            val align = when (b.aligns[c]) {
                                MdAlign.Start -> TextAlign.Start
                                MdAlign.Center -> TextAlign.Center
                                MdAlign.End -> TextAlign.End
                                MdAlign.None -> if (header) TextAlign.Center else TextAlign.Start
                            }
                            Box(
                                Modifier
                                    .width(widths[c])
                                    .fillMaxHeight()
                                    .border(0.5.dp, SA.Line2)
                                    .padding(horizontal = CELL_PAD.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    rich(cell, repoUrl),
                                    color = SA.Text,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (header) FontWeight.SemiBold else null,
                                    textAlign = align,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
