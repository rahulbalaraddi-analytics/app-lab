package com.applab.ideadrop.export

import com.applab.ideadrop.data.NoteWithRecordings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportScope(val label: String) { ALL("All ideas"), PENDING("Pending only") }

/** Builds one Markdown file from notes, grouped by category, ready to share with Claude. */
object MarkdownExporter {

    private const val CLAUDE_PROMPT =
        "> **For Claude:** These are my raw voice-note ideas. Group them by project and turn them " +
            "into a detailed, actionable checklist with clear next steps for each idea."

    fun build(notes: List<NoteWithRecordings>, scope: ExportScope, includePrompt: Boolean): String {
        val selected = notes
            .filter { scope == ExportScope.ALL || !it.note.done }
            .sortedBy { it.note.createdAt }
        val stamp = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("# IdeaDrop export")
        sb.appendLine()
        sb.appendLine("Exported ${stamp.format(Date())} · ${selected.size} ideas · ${scope.label}")
        sb.appendLine()
        if (includePrompt) {
            sb.appendLine(CLAUDE_PROMPT)
            sb.appendLine()
        }
        selected
            .groupBy { it.note.category.ifBlank { "Uncategorized" } }
            .toSortedMap(compareBy<String> { it == "Uncategorized" }.thenBy { it.lowercase() })
            .forEach { (category, items) ->
                sb.appendLine("## $category")
                sb.appendLine()
                items.forEach { item ->
                    val n = item.note
                    val status = if (n.done) "done" else "pending"
                    sb.appendLine("### ${n.title}")
                    sb.appendLine("*${stamp.format(Date(n.createdAt))} · $status*")
                    sb.appendLine()
                    sb.appendLine(n.text.ifBlank { "_(no text yet)_" })
                    sb.appendLine()
                }
            }
        return sb.toString()
    }

    fun fileName(): String =
        "ideadrop-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".md"
}
