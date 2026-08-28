// port-lint: source sentence.rs
package io.github.kotlinmania.unicodesegmentation

private enum class StatePart {
    Sot,
    Eot,
    Other,
    CR,
    LF,
    Sep,
    ATerm,
    UpperLower,
    ClosePlus,
    SpPlus,
    STerm,
}

private data class SentenceBreaksState(
    val first: StatePart,
    val second: StatePart,
    val third: StatePart,
    val fourth: StatePart,
) {
    fun next(category: SentenceCat): SentenceBreaksState {
        val nextPart =
            when {
                fourth == StatePart.ClosePlus && category == SentenceCat.Close -> fourth
                fourth == StatePart.SpPlus && category == SentenceCat.Sp -> fourth
                else ->
                    when (category) {
                        SentenceCat.Cr -> StatePart.CR
                        SentenceCat.Lf -> StatePart.LF
                        SentenceCat.Sep -> StatePart.Sep
                        SentenceCat.ATerm -> StatePart.ATerm
                        SentenceCat.Upper,
                        SentenceCat.Lower,
                        -> StatePart.UpperLower
                        SentenceCat.Close -> StatePart.ClosePlus
                        SentenceCat.Sp -> StatePart.SpPlus
                        SentenceCat.STerm -> StatePart.STerm
                        else -> StatePart.Other
                    }
            }
        return if (nextPart == fourth &&
            (fourth == StatePart.ClosePlus || fourth == StatePart.SpPlus)
        ) {
            this
        } else {
            SentenceBreaksState(second, third, fourth, nextPart)
        }
    }

    fun end(): SentenceBreaksState =
        SentenceBreaksState(second, third, fourth, StatePart.Eot)

    fun match1(part: StatePart): Boolean = fourth == part

    fun match2(part1: StatePart, part2: StatePart): Boolean =
        third == part1 && fourth == part2

    fun partAt(index: Int): StatePart =
        when (index) {
            0 -> first
            1 -> second
            2 -> third
            else -> fourth
        }
}

private val INITIAL_STATE =
    SentenceBreaksState(
        StatePart.Sot,
        StatePart.Sot,
        StatePart.Sot,
        StatePart.Sot,
    )

/** An iterator over substrings containing alphanumeric characters after sentence splitting. */
class UnicodeSentences internal constructor(
    private val bounds: USentenceBounds,
) : Sequence<String> {
    override fun iterator(): Iterator<String> =
        bounds.filter(::hasAlphanumericSentence).iterator()
}

/** External iterator for a string's sentence boundaries. */
class USentenceBounds internal constructor(
    private val string: String,
) : Sequence<String> {
    override fun iterator(): Iterator<String> =
        sentenceRanges(string)
            .map { range -> string.substring(range.first, range.second) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asStr(): String = string

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** External iterator for sentence boundaries and offsets. */
class USentenceBoundIndices internal constructor(
    private val string: String,
) : Sequence<SentenceIndex> {
    override fun iterator(): Iterator<SentenceIndex> =
        sentenceRanges(string)
            .map { range -> SentenceIndex(range.first, string.substring(range.first, range.second)) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asStr(): String = string

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** A sentence-boundary item and its offset in the original string. */
data class SentenceIndex(
    val index: Int,
    val value: String,
)

internal fun newSentenceBounds(source: String): USentenceBounds =
    USentenceBounds(source)

internal fun newSentenceBoundIndices(source: String): USentenceBoundIndices =
    USentenceBoundIndices(source)

internal fun newUnicodeSentences(s: String): UnicodeSentences =
    UnicodeSentences(newSentenceBounds(s))

private fun sentenceRanges(string: String): List<Pair<Int, Int>> =
    sentenceBreaks(string).zipWithNext()

private fun sentenceBreaks(source: String): List<Int> {
    val spans = source.codePointSpans()
    val breaks = mutableListOf<Int>()
    var position = 0
    var state = INITIAL_STATE

    for ((index, span) in spans.withIndex()) {
        val positionBefore = position
        val stateBefore = state
        val nextCategory = sentenceCategory(span.codePoint).category
        position = span.end
        state = state.next(nextCategory)

        when {
            stateBefore.match1(StatePart.Sot) -> breaks.add(positionBefore)
            nextCategory == SentenceCat.Lf && stateBefore.match1(StatePart.CR) -> Unit
            stateBefore.match1(StatePart.Sep) ||
                stateBefore.match1(StatePart.CR) ||
                stateBefore.match1(StatePart.LF) -> breaks.add(positionBefore)
            nextCategory == SentenceCat.Extend ||
                nextCategory == SentenceCat.Format -> state = stateBefore
            nextCategory == SentenceCat.Numeric &&
                stateBefore.match1(StatePart.ATerm) -> Unit
            nextCategory == SentenceCat.Upper &&
                stateBefore.match2(StatePart.UpperLower, StatePart.ATerm) -> Unit
            matchSb8(stateBefore, spans, index) -> Unit
            (
                nextCategory == SentenceCat.SContinue ||
                    nextCategory == SentenceCat.STerm ||
                    nextCategory == SentenceCat.ATerm
            ) &&
                matchSb8a(stateBefore) -> Unit
            (
                nextCategory == SentenceCat.Close ||
                    nextCategory == SentenceCat.Sp ||
                    nextCategory == SentenceCat.Sep ||
                    nextCategory == SentenceCat.Cr ||
                    nextCategory == SentenceCat.Lf
            ) &&
                matchSb9(stateBefore) -> Unit
            (
                nextCategory == SentenceCat.Sp ||
                    nextCategory == SentenceCat.Sep ||
                    nextCategory == SentenceCat.Cr ||
                    nextCategory == SentenceCat.Lf
            ) &&
                matchSb8a(stateBefore) -> Unit
            matchSb11(stateBefore) -> breaks.add(positionBefore)
        }
    }

    if (!state.match1(StatePart.Sot) && !state.match1(StatePart.Eot)) {
        state = state.end()
        breaks.add(position)
    }
    return breaks
}

private fun matchSb8(
    state: SentenceBreaksState,
    spans: List<CodePointSpan>,
    start: Int,
): Boolean {
    var index = if (state.partAt(3) == StatePart.SpPlus) 2 else 3
    if (state.partAt(index) == StatePart.ClosePlus) {
        index -= 1
    }

    if (state.partAt(index) == StatePart.ATerm) {
        for (cursor in start until spans.size) {
            when (sentenceCategory(spans[cursor].codePoint).category) {
                SentenceCat.Lower -> return true
                SentenceCat.OLetter,
                SentenceCat.Upper,
                SentenceCat.Sep,
                SentenceCat.Cr,
                SentenceCat.Lf,
                SentenceCat.STerm,
                SentenceCat.ATerm,
                -> return false
                else -> Unit
            }
        }
    }
    return false
}

private fun matchSb8a(state: SentenceBreaksState): Boolean {
    var index = if (state.partAt(3) == StatePart.SpPlus) 2 else 3
    if (state.partAt(index) == StatePart.ClosePlus) {
        index -= 1
    }
    return state.partAt(index) == StatePart.STerm || state.partAt(index) == StatePart.ATerm
}

private fun matchSb9(state: SentenceBreaksState): Boolean {
    val index = if (state.partAt(3) == StatePart.ClosePlus) 2 else 3
    return state.partAt(index) == StatePart.STerm || state.partAt(index) == StatePart.ATerm
}

private fun matchSb11(state: SentenceBreaksState): Boolean {
    var index =
        when (state.partAt(3)) {
            StatePart.Sep,
            StatePart.CR,
            StatePart.LF,
            -> 2
            else -> 3
        }
    if (state.partAt(index) == StatePart.SpPlus) {
        index -= 1
    }
    if (state.partAt(index) == StatePart.ClosePlus) {
        index -= 1
    }
    return state.partAt(index) == StatePart.STerm || state.partAt(index) == StatePart.ATerm
}

private fun hasAlphanumericSentence(text: String): Boolean =
    text.codePointSpans().any { isAlphanumeric(it.codePoint) }
