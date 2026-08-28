// port-lint: tests sentence.rs
package io.github.kotlinmania.unicodesegmentation

import kotlin.test.Test
import kotlin.test.assertEquals

class SentenceTest {
    @Test
    fun splitSentenceBoundsBasic() {
        val bounds =
            "Mr. Fox jumped. [...] The dog was too lazy."
                .splitSentenceBounds()
                .toList()

        assertEquals(
            listOf("Mr. ", "Fox jumped. ", "[...] ", "The dog was too lazy."),
            bounds,
        )
    }

    @Test
    fun unicodeSentencesBasic() {
        val sentences =
            "Mr. Fox jumped. [...] The dog was too lazy."
                .unicodeSentences()
                .toList()

        assertEquals(listOf("Mr. ", "Fox jumped. ", "The dog was too lazy."), sentences)
    }

    @Test
    fun splitSentenceBoundIndicesOffsets() {
        val bounds =
            "Hello! How are you?"
                .splitSentenceBoundIndices()
                .toList()

        assertEquals(
            listOf(
                SentenceIndex(0, "Hello! "),
                SentenceIndex(7, "How are you?"),
            ),
            bounds,
        )
    }
}
