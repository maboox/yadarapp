package com.yadavard.app

/**
 * Lists inside notes, kept as plain text so backups, sharing and the assistant all just see text:
 * one item per line, "• " bullet, "☐ " to-do, "☑ " done, "1. " numbered.
 */
object NoteText {
    const val BULLET = "• "
    const val TODO = "☐ "
    const val DONE = "☑ "

    enum class Kind { TEXT, BULLET, TODO, DONE, NUMBER }

    data class Line(val kind: Kind, val text: String, val number: Int = 0)

    private val numbered = Regex("""^\s*([0-9۰-۹]+)[.)]\s+(.*)$""")

    fun parse(line: String): Line {
        val l = line.trimStart()
        return when {
            l.startsWith("☐") -> Line(Kind.TODO, l.drop(1).trimStart())
            l.startsWith("☑") || l.startsWith("✅") || l.startsWith("✔") -> Line(Kind.DONE, l.drop(1).trimStart())
            l.startsWith("[ ]") -> Line(Kind.TODO, l.drop(3).trimStart())
            l.startsWith("[x]", ignoreCase = true) -> Line(Kind.DONE, l.drop(3).trimStart())
            l.startsWith("•") || l.startsWith("- ") || l.startsWith("* ") -> Line(Kind.BULLET, l.drop(1).trimStart())
            else -> numbered.matchEntire(l)?.let { m ->
                Line(Kind.NUMBER, m.groupValues[2], Dates.asciiDigits(m.groupValues[1], 4).toIntOrNull() ?: 0)
            } ?: Line(Kind.TEXT, line)
        }
    }

    private fun prefix(kind: Kind, number: Int) = when (kind) {
        Kind.TEXT -> ""
        Kind.BULLET -> BULLET
        Kind.TODO -> TODO
        Kind.DONE -> DONE
        Kind.NUMBER -> "$number. "
    }

    fun render(line: Line): String = prefix(line.kind, line.number) + line.text

    private fun norm(s: String) = s.lowercase().replace('ي', 'ی').replace('ك', 'ک').replace('‌', ' ')
        .replace(Regex("\\s+"), " ").trim()

    private fun matches(text: String, item: String): Boolean {
        val a = norm(text)
        val b = norm(item)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || (b.length >= 2 && a.contains(b)) || (a.length >= 2 && b.contains(a))
    }

    /** Ticks, unticks or removes list items named by the assistant (matched by meaning-ish text, first match wins). */
    fun edit(body: String, check: List<String> = emptyList(), uncheck: List<String> = emptyList(),
             remove: List<String> = emptyList()): String {
        if (check.isEmpty() && uncheck.isEmpty() && remove.isEmpty()) return body
        val lines = body.split('\n').map { parse(it) }.toMutableList()
        val removed = mutableSetOf<Int>()
        fun find(item: String, kinds: Set<Kind>) = lines.indices.firstOrNull { i ->
            i !in removed && lines[i].kind in kinds && matches(lines[i].text, item)
        }
        check.forEach { item ->
            (find(item, setOf(Kind.TODO, Kind.BULLET, Kind.NUMBER)) ?: find(item, setOf(Kind.TEXT)))
                ?.let { lines[it] = lines[it].copy(kind = Kind.DONE) }
        }
        uncheck.forEach { item -> find(item, setOf(Kind.DONE))?.let { lines[it] = lines[it].copy(kind = Kind.TODO) } }
        remove.forEach { item ->
            find(item, setOf(Kind.TODO, Kind.DONE, Kind.BULLET, Kind.NUMBER, Kind.TEXT))?.let { removed += it }
        }
        return lines.filterIndexed { i, _ -> i !in removed }.joinToString("\n") { render(it) }
    }

    /** Flips line [index] between to-do and done. */
    fun toggle(body: String, index: Int): String {
        val lines = body.split('\n').toMutableList()
        if (index !in lines.indices) return body
        val line = parse(lines[index])
        lines[index] = render(when (line.kind) {
            Kind.TODO -> line.copy(kind = Kind.DONE)
            Kind.DONE -> line.copy(kind = Kind.TODO)
            else -> return body
        })
        return lines.joinToString("\n")
    }

    /** Toolbar: gives line [index] the list style [kind], or plain text when it already has it. */
    fun setKind(body: String, index: Int, kind: Kind): String {
        val lines = body.split('\n').toMutableList()
        if (index !in lines.indices) return body
        val line = parse(lines[index])
        val same = line.kind == kind || (kind == Kind.TODO && line.kind == Kind.DONE)
        val number = if (kind == Kind.NUMBER) {
            (index - 1 downTo 0).map { parse(lines[it]) }.takeWhile { it.kind == Kind.NUMBER }.firstOrNull()?.number?.plus(1) ?: 1
        } else 0
        lines[index] = render(if (same) Line(Kind.TEXT, line.text) else Line(kind, line.text, number))
        return lines.joinToString("\n")
    }

    /**
     * Called when the user typed a line break: continues the list on the new line ("☐ ", next number…), or ends
     * the list when Enter is pressed on an empty item. Returns the new text and cursor, or null to keep [new].
     */
    fun continueList(old: String, new: String, cursor: Int): Pair<String, Int>? {
        if (new.length != old.length + 1 || cursor <= 0 || cursor > new.length || new[cursor - 1] != '\n') return null
        val start = new.lastIndexOf('\n', cursor - 2) + 1
        val previous = parse(new.substring(start, cursor - 1))
        if (previous.kind == Kind.TEXT) return null
        if (previous.text.isBlank()) { // Enter on an empty item: leave the list
            return (new.substring(0, start) + new.substring(cursor)) to start
        }
        val next = prefix(if (previous.kind == Kind.DONE) Kind.TODO else previous.kind, previous.number + 1)
        return (new.substring(0, cursor) + next + new.substring(cursor)) to cursor + next.length
    }

    /** Index of the line that contains character [offset]. */
    fun lineAt(text: String, offset: Int): Int = text.take(offset.coerceIn(0, text.length)).count { it == '\n' }
}
