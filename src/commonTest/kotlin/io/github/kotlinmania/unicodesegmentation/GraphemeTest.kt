// port-lint: tests grapheme.rs
package io.github.kotlinmania.unicodesegmentation

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemeTest {
    @Test
    fun testGraphemeCursorRisPrecontext() {
        val s = "\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDFA\uD83C\uDDF8"
        val c = GraphemeCursor(8, s.length, true)
        assertEquals(
            Result.failure(GraphemeIncomplete.PreContext(4)),
            c.isBoundary(s.substring(4), 4),
        )
        c.provideContext(s.substring(0, 4), 0)
        assertEquals(Result.success(true), c.isBoundary(s.substring(4), 4))
    }

    @Test
    fun testGraphemeCursorChunkStartRequirePrecontext() {
        val s = "\r\n"
        val c = GraphemeCursor(1, s.length, true)
        assertEquals(
            Result.failure(GraphemeIncomplete.PreContext(1)),
            c.isBoundary(s.substring(1), 1),
        )
        c.provideContext(s.substring(0, 1), 0)
        assertEquals(Result.success(false), c.isBoundary(s.substring(1), 1))
    }

    @Test
    fun testGraphemeCursorPrevBoundary() {
        val s = "abcd"
        val c = GraphemeCursor(3, s.length, true)
        assertEquals(
            Result.failure(GraphemeIncomplete.PrevChunk),
            c.prevBoundary(s.substring(2), 2),
        )
        assertEquals(Result.success(2), c.prevBoundary(s.substring(0, 2), 0))
    }

    @Test
    fun testGraphemeCursorPrevBoundaryChunkStart() {
        val s = "abcd"
        val c = GraphemeCursor(2, s.length, true)
        assertEquals(
            Result.failure(GraphemeIncomplete.PrevChunk),
            c.prevBoundary(s.substring(2), 2),
        )
        assertEquals(Result.success(1), c.prevBoundary(s.substring(0, 2), 0))
    }
}
