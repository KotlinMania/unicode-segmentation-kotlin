// port-lint: source lib.rs
package io.github.kotlinmania.unicodesegmentation

/**
 * Iterators which split strings on word boundaries, according to the Unicode Standard Annex #29
 * rules.
 */

/**
 * Methods for segmenting strings according to Unicode Standard Annex #29.
 */
object UnicodeSegmentation {
    /**
     * Returns a sequence over the grapheme clusters of [text].
     *
     * If [isExtended] is true, the sequence is over the extended grapheme clusters; otherwise, the
     * sequence is over the legacy grapheme clusters. The Unicode Standard Annex #29 recommends
     * extended grapheme cluster boundaries for general processing.
     */
    fun graphemes(text: String, isExtended: Boolean): Graphemes =
        newGraphemes(text, isExtended)

    /**
     * Returns a sequence over the grapheme clusters of [text] and their offsets.
     */
    fun graphemeIndices(text: String, isExtended: Boolean): GraphemeIndices =
        newGraphemeIndices(text, isExtended)

    /**
     * Returns a sequence over the words of [text], separated on word boundaries.
     *
     * Here, "words" are just those substrings which, after splitting on word boundaries, contain
     * any alphanumeric characters.
     */
    fun unicodeWords(text: String): UnicodeWords = newUnicodeWords(text)

    /**
     * Returns a sequence over the words of [text], separated on word boundaries, and their
     * offsets.
     */
    fun unicodeWordIndices(text: String): UnicodeWordIndices = newUnicodeWordIndices(text)

    /**
     * Returns a sequence over substrings of [text] separated on word boundaries.
     *
     * The concatenation of the substrings returned by this function is just the original string.
     */
    fun splitWordBounds(text: String): UWordBounds = newWordBounds(text)

    /**
     * Returns a sequence over substrings of [text], split on word boundaries, and their offsets.
     */
    fun splitWordBoundIndices(text: String): UWordBoundIndices = newWordBoundIndices(text)

    /**
     * Returns a sequence over substrings of [text] separated on sentence boundaries.
     *
     * Here, "sentences" are just those substrings which, after splitting on sentence boundaries,
     * contain any alphanumeric characters.
     */
    fun unicodeSentences(text: String): UnicodeSentences = newUnicodeSentences(text)

    /**
     * Returns a sequence over substrings of [text] separated on sentence boundaries.
     *
     * The concatenation of the substrings returned by this function is just the original string.
     */
    fun splitSentenceBounds(text: String): USentenceBounds = newSentenceBounds(text)

    /**
     * Returns a sequence over substrings of [text], split on sentence boundaries, and their
     * offsets.
     */
    fun splitSentenceBoundIndices(text: String): USentenceBoundIndices =
        newSentenceBoundIndices(text)
}

/**
 * Returns a sequence over the grapheme clusters of this string.
 */
fun String.graphemes(isExtended: Boolean): Graphemes =
    UnicodeSegmentation.graphemes(this, isExtended)

/**
 * Returns a sequence over the grapheme clusters of this string and their offsets.
 */
fun String.graphemeIndices(isExtended: Boolean): GraphemeIndices =
    UnicodeSegmentation.graphemeIndices(this, isExtended)

/**
 * Returns a sequence over the words of this string, separated on word boundaries.
 */
fun String.unicodeWords(): UnicodeWords = UnicodeSegmentation.unicodeWords(this)

/**
 * Returns a sequence over the words of this string, separated on word boundaries, and their
 * offsets.
 */
fun String.unicodeWordIndices(): UnicodeWordIndices =
    UnicodeSegmentation.unicodeWordIndices(this)

/**
 * Returns a sequence over substrings of this string separated on word boundaries.
 */
fun String.splitWordBounds(): UWordBounds = UnicodeSegmentation.splitWordBounds(this)

/**
 * Returns a sequence over substrings of this string, split on word boundaries, and their offsets.
 */
fun String.splitWordBoundIndices(): UWordBoundIndices =
    UnicodeSegmentation.splitWordBoundIndices(this)

/**
 * Returns a sequence over substrings of this string separated on sentence boundaries.
 */
fun String.unicodeSentences(): UnicodeSentences =
    UnicodeSegmentation.unicodeSentences(this)

/**
 * Returns a sequence over substrings of this string separated on sentence boundaries.
 */
fun String.splitSentenceBounds(): USentenceBounds =
    UnicodeSegmentation.splitSentenceBounds(this)

/**
 * Returns a sequence over substrings of this string, split on sentence boundaries, and their
 * offsets.
 */
fun String.splitSentenceBoundIndices(): USentenceBoundIndices =
    UnicodeSegmentation.splitSentenceBoundIndices(this)
