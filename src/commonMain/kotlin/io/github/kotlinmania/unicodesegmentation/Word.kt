// port-lint: source word.rs
package io.github.kotlinmania.unicodesegmentation

/**
 * An iterator over the substrings of a string which, after splitting the string on word
 * boundaries, contain any alphanumeric characters.
 *
 * This class is created by the [unicodeWords] method. See its documentation for more.
 */
class UnicodeWords internal constructor(
    private val bounds: UWordBounds,
) : Sequence<String> {
    override fun iterator(): Iterator<String> =
        bounds.filter(::hasAlphanumeric).iterator()
}

/**
 * An iterator over the substrings of a string which, after splitting the string on word
 * boundaries, contain any alphanumeric characters. This iterator also provides offsets for each
 * substring.
 *
 * This class is created by the [unicodeWordIndices] method. See its documentation for more.
 */
class UnicodeWordIndices internal constructor(
    private val bounds: UWordBoundIndices,
) : Sequence<WordIndex> {
    override fun iterator(): Iterator<WordIndex> =
        bounds.filter { hasAlphanumeric(it.value) }.iterator()
}

/** External iterator for a string's word boundaries. */
class UWordBounds internal constructor(
    private val string: String,
) : Sequence<String> {
    override fun iterator(): Iterator<String> =
        wordRanges(string)
            .map { range -> string.substring(range.first, range.second) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** External iterator for word boundaries and offsets. */
class UWordBoundIndices internal constructor(
    private val string: String,
) : Sequence<WordIndex> {
    override fun iterator(): Iterator<WordIndex> =
        wordRanges(string)
            .map { range -> WordIndex(range.first, string.substring(range.first, range.second)) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** A word-boundary item and its offset in the original string. */
data class WordIndex(
    val index: Int,
    val value: String,
)

private sealed class UWordBoundsState {
    data object Start : UWordBoundsState()

    data object Letter : UWordBoundsState()

    data object HLetter : UWordBoundsState()

    data object Numeric : UWordBoundsState()

    data object Katakana : UWordBoundsState()

    data object ExtendNumLet : UWordBoundsState()

    data class Regional(
        val state: RegionalState,
    ) : UWordBoundsState()

    data class FormatExtend(
        val type: FormatExtendType,
    ) : UWordBoundsState()

    data object Zwj : UWordBoundsState()

    data object Emoji : UWordBoundsState()

    data object WSegSpace : UWordBoundsState()
}

private enum class FormatExtendType {
    AcceptAny,
    AcceptNone,
    RequireLetter,
    RequireHLetter,
    AcceptQLetter,
    RequireNumeric,
}

private enum class RegionalState {
    Half,
    Full,
    Unknown,
}

private data class WordCodePoint(
    val span: CodePointSpan,
    val category: WordCat,
)

private fun isEmoji(codePoint: Int): Boolean =
    emojiCategory(codePoint).category == EmojiCat.ExtendedPictographic

private fun wordRanges(string: String): List<Pair<Int, Int>> =
    wordBoundaries(string).zipWithNext()

private fun wordBoundaries(string: String): List<Int> {
    val codePoints =
        string
            .codePointSpans()
            .map { span -> WordCodePoint(span, wordCategory(span.codePoint).category) }
    if (codePoints.isEmpty()) return listOf(0)
    val boundaries = mutableListOf(0)
    var index = 0
    while (index < codePoints.size) {
        val next = nextWordBoundary(codePoints, index)
        boundaries.add(if (next < codePoints.size) codePoints[next].span.start else string.length)
        index = next
    }
    return boundaries
}

private fun nextWordBoundary(codePoints: List<WordCodePoint>, start: Int): Int {
    var takeCurrent = true
    var index = start
    var saveIndex = start
    var state: UWordBoundsState = UWordBoundsState.Start
    var category = WordCat.Any
    var saveCategory = WordCat.Any
    var skippedFormatExtend = false
    var cursor = start

    while (cursor < codePoints.size) {
        index = cursor
        val previousZwj = category == WordCat.Zwj
        category = codePoints[cursor].category

        if (state != UWordBoundsState.Start) {
            when (category) {
                WordCat.Extend,
                WordCat.Format,
                WordCat.Zwj,
                -> {
                    skippedFormatExtend = true
                    cursor += 1
                    continue
                }
                else -> Unit
            }
        }

        if (previousZwj && isEmoji(codePoints[cursor].span.codePoint)) {
            state = UWordBoundsState.Emoji
            cursor += 1
            continue
        }

        val nextState =
            nextWordState(
                state = state,
                category = category,
                codePoints = codePoints,
                index = index,
                skippedFormatExtend = skippedFormatExtend,
                save = { savedIndex, savedCategory ->
                    saveIndex = savedIndex
                    saveCategory = savedCategory
                },
                breakBeforeCurrent = { takeCurrent = false },
                advanceIndex = { amount -> index += amount },
            )

        if (nextState == null) break
        state = nextState
        cursor += 1
    }

    if (state is UWordBoundsState.FormatExtend) {
        when (state.type) {
            FormatExtendType.RequireLetter,
            FormatExtendType.RequireHLetter,
            FormatExtendType.RequireNumeric,
            -> {
                index = saveIndex
                category = saveCategory
                takeCurrent = false
            }
            else -> Unit
        }
    }

    val next = if (takeCurrent) index + 1 else index
    return next.coerceIn(start + 1, codePoints.size)
}

private fun nextWordState(
    state: UWordBoundsState,
    category: WordCat,
    codePoints: List<WordCodePoint>,
    index: Int,
    skippedFormatExtend: Boolean,
    save: (Int, WordCat) -> Unit,
    breakBeforeCurrent: () -> Unit,
    advanceIndex: (Int) -> Unit,
): UWordBoundsState? {
    val nextCategory = codePoints.getOrNull(index + 1)?.category
    return when (state) {
        UWordBoundsState.Start ->
            when (category) {
                WordCat.Cr -> {
                    if (nextCategory == WordCat.Lf) advanceIndex(1)
                    null
                }
                WordCat.ALetter -> UWordBoundsState.Letter
                WordCat.HebrewLetter -> UWordBoundsState.HLetter
                WordCat.Numeric -> UWordBoundsState.Numeric
                WordCat.Katakana -> UWordBoundsState.Katakana
                WordCat.ExtendNumLet -> UWordBoundsState.ExtendNumLet
                WordCat.RegionalIndicator -> UWordBoundsState.Regional(RegionalState.Half)
                WordCat.Lf,
                WordCat.Newline,
                -> null
                WordCat.Zwj -> UWordBoundsState.Zwj
                WordCat.WSegSpace -> UWordBoundsState.WSegSpace
                else -> {
                    if (nextCategory == WordCat.Format ||
                        nextCategory == WordCat.Extend ||
                        nextCategory == WordCat.Zwj
                    ) {
                        UWordBoundsState.FormatExtend(FormatExtendType.AcceptNone)
                    } else {
                        null
                    }
                }
            }
        UWordBoundsState.WSegSpace ->
            when {
                category == WordCat.WSegSpace && !skippedFormatExtend -> UWordBoundsState.WSegSpace
                else -> {
                    breakBeforeCurrent()
                    null
                }
            }
        UWordBoundsState.Zwj -> {
            breakBeforeCurrent()
            null
        }
        UWordBoundsState.Letter,
        UWordBoundsState.HLetter,
        -> letterState(state, category, index, save, breakBeforeCurrent)
        UWordBoundsState.Numeric -> numericState(category, index, save, breakBeforeCurrent)
        UWordBoundsState.Katakana ->
            when (category) {
                WordCat.Katakana -> UWordBoundsState.Katakana
                WordCat.ExtendNumLet -> UWordBoundsState.ExtendNumLet
                else -> {
                    breakBeforeCurrent()
                    null
                }
            }
        UWordBoundsState.ExtendNumLet ->
            when (category) {
                WordCat.ExtendNumLet -> UWordBoundsState.ExtendNumLet
                WordCat.ALetter -> UWordBoundsState.Letter
                WordCat.HebrewLetter -> UWordBoundsState.HLetter
                WordCat.Numeric -> UWordBoundsState.Numeric
                WordCat.Katakana -> UWordBoundsState.Katakana
                else -> {
                    breakBeforeCurrent()
                    null
                }
            }
        is UWordBoundsState.Regional -> regionalState(state.state, category, breakBeforeCurrent)
        UWordBoundsState.Emoji -> {
            breakBeforeCurrent()
            null
        }
        is UWordBoundsState.FormatExtend ->
            formatExtendState(
                type = state.type,
                category = category,
                breakBeforeCurrent = breakBeforeCurrent,
            )
    }
}

private fun letterState(
    state: UWordBoundsState,
    category: WordCat,
    index: Int,
    save: (Int, WordCat) -> Unit,
    breakBeforeCurrent: () -> Unit,
): UWordBoundsState? =
    when (category) {
        WordCat.ALetter -> UWordBoundsState.Letter
        WordCat.HebrewLetter -> UWordBoundsState.HLetter
        WordCat.Numeric -> UWordBoundsState.Numeric
        WordCat.ExtendNumLet -> UWordBoundsState.ExtendNumLet
        WordCat.DoubleQuote if state == UWordBoundsState.HLetter -> {
            save(index, category)
            UWordBoundsState.FormatExtend(FormatExtendType.RequireHLetter)
        }
        WordCat.SingleQuote if state == UWordBoundsState.HLetter ->
            UWordBoundsState.FormatExtend(FormatExtendType.AcceptQLetter)
        WordCat.MidLetter,
        WordCat.MidNumLet,
        WordCat.SingleQuote,
        -> {
            save(index, category)
            UWordBoundsState.FormatExtend(FormatExtendType.RequireLetter)
        }
        else -> {
            breakBeforeCurrent()
            null
        }
    }

private fun numericState(
    category: WordCat,
    index: Int,
    save: (Int, WordCat) -> Unit,
    breakBeforeCurrent: () -> Unit,
): UWordBoundsState? =
    when (category) {
        WordCat.Numeric -> UWordBoundsState.Numeric
        WordCat.ALetter -> UWordBoundsState.Letter
        WordCat.HebrewLetter -> UWordBoundsState.HLetter
        WordCat.ExtendNumLet -> UWordBoundsState.ExtendNumLet
        WordCat.MidNum,
        WordCat.MidNumLet,
        WordCat.SingleQuote,
        -> {
            save(index, category)
            UWordBoundsState.FormatExtend(FormatExtendType.RequireNumeric)
        }
        else -> {
            breakBeforeCurrent()
            null
        }
    }

private fun regionalState(
    state: RegionalState,
    category: WordCat,
    breakBeforeCurrent: () -> Unit,
): UWordBoundsState? =
    when (state) {
        RegionalState.Full -> {
            breakBeforeCurrent()
            null
        }
        RegionalState.Half ->
            when (category) {
                WordCat.RegionalIndicator -> UWordBoundsState.Regional(RegionalState.Full)
                else -> {
                    breakBeforeCurrent()
                    null
                }
            }
        RegionalState.Unknown -> {
            breakBeforeCurrent()
            null
        }
    }

private fun formatExtendState(
    type: FormatExtendType,
    category: WordCat,
    breakBeforeCurrent: () -> Unit,
): UWordBoundsState? =
    when {
        type == FormatExtendType.RequireNumeric && category == WordCat.Numeric ->
            UWordBoundsState.Numeric
        (type == FormatExtendType.RequireLetter || type == FormatExtendType.AcceptQLetter) &&
            category == WordCat.ALetter -> UWordBoundsState.Letter
        (type == FormatExtendType.RequireLetter || type == FormatExtendType.AcceptQLetter) &&
            category == WordCat.HebrewLetter -> UWordBoundsState.HLetter
        type == FormatExtendType.RequireHLetter && category == WordCat.HebrewLetter ->
            UWordBoundsState.HLetter
        type == FormatExtendType.AcceptNone || type == FormatExtendType.AcceptQLetter -> {
            breakBeforeCurrent()
            null
        }
        else -> null
    }

private fun hasAlphanumeric(text: String): Boolean =
    text.codePointSpans().any { isAlphanumeric(it.codePoint) }

internal fun newWordBounds(s: String): UWordBounds = UWordBounds(s)

internal fun newWordBoundIndices(s: String): UWordBoundIndices = UWordBoundIndices(s)

internal fun newUnicodeWords(s: String): UnicodeWords = UnicodeWords(newWordBounds(s))

internal fun newUnicodeWordIndices(s: String): UnicodeWordIndices =
    UnicodeWordIndices(newWordBoundIndices(s))
