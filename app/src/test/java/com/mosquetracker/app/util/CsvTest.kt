package com.mosquetracker.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun plainTextIsUntouched() {
        assertEquals("Masjid Hassan II", Csv.cell("Masjid Hassan II"))
    }

    @Test
    fun separatorsAndQuotesAreQuoted() {
        assertEquals("\"Al Nour, Fes\"", Csv.cell("Al Nour, Fes"))
        assertEquals("\"The \"\"big\"\" one\"", Csv.cell("The \"big\" one"))
        assertEquals("\"two\nlines\"", Csv.cell("two\nlines"))
    }

    @Test
    fun formulaStartsAreDefused() {
        assertEquals("'=1+1", Csv.cell("=1+1"))
        assertEquals("'+SUM(A1)", Csv.cell("+SUM(A1)"))
        assertEquals("'-2", Csv.cell("-2"))
        assertEquals("'@cmd", Csv.cell("@cmd"))
        assertEquals("'\tx", Csv.cell("\tx"))
    }

    @Test
    fun defusedFormulaWithCommaIsStillQuoted() {
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"", Csv.cell("=HYPERLINK(\"http://x\",\"y\")"))
    }

    @Test
    fun rowJoinsEscapedCells() {
        assertEquals("a,\"b,c\",'=d", Csv.row("a", "b,c", "=d"))
    }

    @Test
    fun emptyCellStaysEmpty() {
        assertEquals("", Csv.cell(""))
    }
}
