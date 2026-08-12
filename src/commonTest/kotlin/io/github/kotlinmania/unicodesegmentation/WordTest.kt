// port-lint: source src/word.rs
package io.github.kotlinmania.unicodesegmentation

import kotlin.test.Test
import kotlin.test.assertEquals

class WordTest {
    @Test
    fun graphemesFollowTheReadmeExample() {
        val graphemes =
            "a\u0310e\u0301o\u0308\u0332\r\n"
                .graphemes(isExtended = true)
                .toList()

        assertEquals(listOf("a\u0310", "e\u0301", "o\u0308\u0332", "\r\n"), graphemes)
    }

    @Test
    fun graphemeIndicesReturnOffsets() {
        val indices =
            "a\u0310e\u0301o\u0308\u0332\r\n"
                .graphemeIndices(isExtended = true)
                .toList()

        assertEquals(
            listOf(
                GraphemeIndex(0, "a\u0310"),
                GraphemeIndex(2, "e\u0301"),
                GraphemeIndex(4, "o\u0308\u0332"),
                GraphemeIndex(7, "\r\n"),
            ),
            indices,
        )
    }

    @Test
    fun unicodeWordsFollowTheReadmeExample() {
        val words =
            "The quick (\"brown\") fox can't jump 32.3 feet, right?"
                .unicodeWords()
                .toList()

        assertEquals(
            listOf("The", "quick", "brown", "fox", "can't", "jump", "32.3", "feet", "right"),
            words,
        )
    }

    @Test
    fun splitWordBoundsKeepsSeparators() {
        val bounds = "The quick (\"brown\")  fox".splitWordBounds().toList()

        assertEquals(
            listOf("The", " ", "quick", " ", "(", "\"", "brown", "\"", ")", "  ", "fox"),
            bounds,
        )
    }

    @Test
    fun splitWordBoundIndicesReturnOffsets() {
        val bounds = "Brr, it's 29.3\u00b0F!".splitWordBoundIndices().toList()

        assertEquals(
            listOf(
                WordIndex(0, "Brr"),
                WordIndex(3, ","),
                WordIndex(4, " "),
                WordIndex(5, "it's"),
                WordIndex(9, " "),
                WordIndex(10, "29.3"),
                WordIndex(14, "\u00b0"),
                WordIndex(15, "F"),
                WordIndex(16, "!"),
            ),
            bounds,
        )
    }

    @Test
    fun unicodeWordIndicesFilterSeparators() {
        val indices = "The quick (\"brown\") fox".unicodeWordIndices().toList()

        assertEquals(
            listOf(
                WordIndex(0, "The"),
                WordIndex(4, "quick"),
                WordIndex(12, "brown"),
                WordIndex(20, "fox"),
            ),
            indices,
        )
    }

    @Test
    fun splitSentenceBoundsFollowTheReadmeExample() {
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
    fun unicodeSentencesFilterNonAlphanumericSentences() {
        val sentences =
            "Mr. Fox jumped. [...] The dog was too lazy."
                .unicodeSentences()
                .toList()

        assertEquals(listOf("Mr. ", "Fox jumped. ", "The dog was too lazy."), sentences)
    }
}
