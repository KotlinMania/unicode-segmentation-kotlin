// port-lint: source grapheme.rs
package io.github.kotlinmania.unicodesegmentation

/** External sequence for grapheme clusters and offsets. */
class GraphemeIndices internal constructor(
    private val string: String,
    private val isExtended: Boolean,
) : Sequence<GraphemeIndex> {
    override fun iterator(): Iterator<GraphemeIndex> =
        graphemeRanges(string, isExtended)
            .map { range -> GraphemeIndex(range.first, string.substring(range.first, range.second)) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** External sequence for a string's grapheme clusters. */
class Graphemes internal constructor(
    private val string: String,
    private val isExtended: Boolean,
) : Sequence<String> {
    override fun iterator(): Iterator<String> =
        graphemeRanges(string, isExtended)
            .map { range -> string.substring(range.first, range.second) }
            .iterator()

    /** View the underlying data as a slice of the original string. */
    fun asString(): String = string
}

/** A grapheme cluster and its offset in the original string. */
data class GraphemeIndex(
    val index: Int,
    val value: String,
)

internal fun newGraphemes(s: String, isExtended: Boolean): Graphemes =
    Graphemes(s, isExtended)

internal fun newGraphemeIndices(s: String, isExtended: Boolean): GraphemeIndices =
    GraphemeIndices(s, isExtended)

private enum class GraphemeState {
    Unknown,
    NotBreak,
    Break,
    InCbConsonant,
    Regional,
    Emoji,
}

/** Cursor-based segmenter for grapheme clusters. */
class GraphemeCursor(
    offset: Int,
    private val len: Int,
    private val isExtended: Boolean,
) {
    private var offset: Int = offset
    private var state: GraphemeState =
        if (offset == 0 || offset == len) GraphemeState.Break else GraphemeState.Unknown

    /** Set the cursor to a new location in the same string. */
    fun setCursor(offset: Int) {
        if (offset != this.offset) {
            this.offset = offset
            state = if (offset == 0 || offset == len) GraphemeState.Break else GraphemeState.Unknown
        }
    }

    /** The current offset of the cursor. */
    fun curCursor(): Int = offset

    /** Provide additional pre-context when it is needed to decide a boundary. */
    fun provideContext(chunk: String, chunkStart: Int) {
        if (chunkStart + chunk.length > len) {
            throw IllegalArgumentException("chunk extends past the cursor's string length")
        }
        state = GraphemeState.Unknown
    }

    /** Determine whether the current cursor location is a grapheme cluster boundary. */
    fun isBoundary(chunk: String, chunkStart: Int): Result<Boolean> {
        if (offset == 0 || offset == len) return Result.success(true)
        if (offset < chunkStart || offset > chunkStart + chunk.length) {
            return Result.failure(GraphemeIncomplete.InvalidOffset)
        }
        if (chunkStart != 0 || chunkStart + chunk.length != len) {
            return Result.failure(GraphemeIncomplete.PreContext(chunkStart))
        }
        state = if (offset in graphemeBoundaries(chunk, isExtended)) {
            GraphemeState.Break
        } else {
            GraphemeState.NotBreak
        }
        return Result.success(state == GraphemeState.Break)
    }

    /** Find the next boundary after the current cursor position. */
    fun nextBoundary(chunk: String, chunkStart: Int): Result<Int?> {
        if (offset == len) return Result.success(null)
        if (offset < chunkStart || offset > chunkStart + chunk.length) {
            return Result.failure(GraphemeIncomplete.InvalidOffset)
        }
        if (chunkStart != 0 || chunkStart + chunk.length != len) {
            return Result.failure(GraphemeIncomplete.NextChunk)
        }
        val next = graphemeBoundaries(chunk, isExtended).firstOrNull { it > offset }
        offset = next ?: len
        state = if (next == null) GraphemeState.Break else GraphemeState.Unknown
        return Result.success(next)
    }

    /** Find the previous boundary before the current cursor position. */
    fun prevBoundary(chunk: String, chunkStart: Int): Result<Int?> {
        if (offset == 0) return Result.success(null)
        if (offset < chunkStart || offset > chunkStart + chunk.length) {
            return Result.failure(GraphemeIncomplete.InvalidOffset)
        }
        if (chunkStart != 0 || chunkStart + chunk.length != len) {
            return Result.failure(GraphemeIncomplete.PrevChunk)
        }
        val previous = graphemeBoundaries(chunk, isExtended).lastOrNull { it < offset }
        offset = previous ?: 0
        state = if (previous == null) GraphemeState.Break else GraphemeState.Unknown
        return Result.success(previous)
    }
}

/** An error return indicating that not enough content was available in the provided chunk. */
sealed class GraphemeIncomplete(message: String) : RuntimeException(message) {
    /** More pre-context is needed. */
    data class PreContext(val offset: Int) : GraphemeIncomplete("more pre-context is needed")

    /** The cursor is moving past the beginning of the current chunk. */
    data object PrevChunk : GraphemeIncomplete("previous chunk is needed")

    /** The cursor is moving past the end of the current chunk. */
    data object NextChunk : GraphemeIncomplete("next chunk is needed")

    /** The chunk given does not contain the cursor position. */
    data object InvalidOffset : GraphemeIncomplete("invalid cursor offset for chunk")
}

private data class GraphemeCodePoint(
    val span: CodePointSpan,
    val category: GraphemeCat,
)

private fun graphemeRanges(string: String, isExtended: Boolean): List<Pair<Int, Int>> {
    val boundaries = graphemeBoundaries(string, isExtended)
    return boundaries.zipWithNext()
}

private fun graphemeBoundaries(string: String, isExtended: Boolean): List<Int> {
    val codePoints = string.codePointSpans()
        .map { span -> GraphemeCodePoint(span, graphemeCategory(span.codePoint).category) }
    if (codePoints.isEmpty()) return listOf(0)
    val boundaries = mutableListOf(0)
    for (index in 1 until codePoints.size) {
        if (isGraphemeBoundary(codePoints, index, isExtended)) {
            boundaries.add(codePoints[index].span.start)
        }
    }
    boundaries.add(string.length)
    return boundaries
}

private fun isGraphemeBoundary(
    codePoints: List<GraphemeCodePoint>,
    index: Int,
    isExtended: Boolean,
): Boolean {
    val before = codePoints[index - 1].category
    val after = codePoints[index].category
    return when {
        before == GraphemeCat.Cr && after == GraphemeCat.Lf -> false
        before == GraphemeCat.Control ||
            before == GraphemeCat.Cr ||
            before == GraphemeCat.Lf -> true
        after == GraphemeCat.Control ||
            after == GraphemeCat.Cr ||
            after == GraphemeCat.Lf -> true
        before == GraphemeCat.L &&
            (after == GraphemeCat.L ||
                after == GraphemeCat.V ||
                after == GraphemeCat.Lv ||
                after == GraphemeCat.LvT) -> false
        (before == GraphemeCat.Lv || before == GraphemeCat.V) &&
            (after == GraphemeCat.V || after == GraphemeCat.T) -> false
        (before == GraphemeCat.LvT || before == GraphemeCat.T) &&
            after == GraphemeCat.T -> false
        after == GraphemeCat.Extend || after == GraphemeCat.Zwj -> false
        after == GraphemeCat.SpacingMark -> !isExtended
        before == GraphemeCat.Prepend -> !isExtended
        after == GraphemeCat.InCbConsonant -> !hasIncbConsonantContext(codePoints, index, isExtended)
        before == GraphemeCat.Zwj &&
            after == GraphemeCat.ExtendedPictographic -> !hasEmojiContext(codePoints, index)
        before == GraphemeCat.RegionalIndicator &&
            after == GraphemeCat.RegionalIndicator -> hasEvenRegionalCount(codePoints, index)
        else -> true
    }
}

private fun hasIncbConsonantContext(
    codePoints: List<GraphemeCodePoint>,
    index: Int,
    isExtended: Boolean,
): Boolean {
    if (!isExtended) return false
    var linkerCount = 0
    var cursor = index - 1
    while (cursor >= 0) {
        val codePoint = codePoints[cursor].span.codePoint
        when {
            isIncbLinker(codePoint) -> linkerCount += 1
            DerivedProperty.incbExtend(codePoint) -> Unit
            else -> return linkerCount > 0 &&
                codePoints[cursor].category == GraphemeCat.InCbConsonant
        }
        cursor -= 1
    }
    return false
}

private fun hasEmojiContext(codePoints: List<GraphemeCodePoint>, index: Int): Boolean {
    var cursor = index - 2
    while (cursor >= 0) {
        when (codePoints[cursor].category) {
            GraphemeCat.Extend -> cursor -= 1
            GraphemeCat.ExtendedPictographic -> return true
            else -> return false
        }
    }
    return false
}

private fun hasEvenRegionalCount(codePoints: List<GraphemeCodePoint>, index: Int): Boolean {
    var count = 0
    var cursor = index - 1
    while (cursor >= 0 && codePoints[cursor].category == GraphemeCat.RegionalIndicator) {
        count += 1
        cursor -= 1
    }
    return count % 2 == 0
}
