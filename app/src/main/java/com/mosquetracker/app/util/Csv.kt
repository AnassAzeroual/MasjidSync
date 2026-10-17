package com.mosquetracker.app.util

/** RFC 4180 cells that a spreadsheet will not run as a formula. */
object Csv {

    private val formulaStarts = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * A user-typed mosque name like `=HYPERLINK(...)` would execute when the export is opened in
     * Excel or Sheets, so such a cell gets a leading apostrophe (shown as text, not evaluated).
     */
    fun cell(value: String): String {
        val safe = if (value.firstOrNull() in formulaStarts) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    fun row(vararg cells: String): String = cells.joinToString(",") { cell(it) }
}
