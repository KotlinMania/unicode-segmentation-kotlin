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
    fun asStr(): String = string

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
    fun asStr(): String = string

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

private enum class PairResult {
    NotBreak,
    Break,
    Extended,
    InCbConsonant,
    Regional,
    Emoji,
}

private fun checkPair(before: GraphemeCat, after: GraphemeCat): PairResult =
    when {
        before == GraphemeCat.Cr && after == GraphemeCat.Lf -> PairResult.NotBreak // GB3
        before == GraphemeCat.Control || before == GraphemeCat.Cr || before == GraphemeCat.Lf -> PairResult.Break // GB4
        after == GraphemeCat.Control || after == GraphemeCat.Cr || after == GraphemeCat.Lf -> PairResult.Break // GB5
        before == GraphemeCat.L &&
            (
                after == GraphemeCat.L ||
                    after == GraphemeCat.V ||
                    after == GraphemeCat.Lv ||
                    after == GraphemeCat.LvT
            ) -> PairResult.NotBreak // GB6
        (before == GraphemeCat.Lv || before == GraphemeCat.V) &&
            (after == GraphemeCat.V || after == GraphemeCat.T) -> PairResult.NotBreak // GB7
        (before == GraphemeCat.LvT || before == GraphemeCat.T) &&
            after == GraphemeCat.T -> PairResult.NotBreak // GB8
        after == GraphemeCat.Extend || after == GraphemeCat.Zwj -> PairResult.NotBreak // GB9
        after == GraphemeCat.SpacingMark -> PairResult.Extended // GB9a
        before == GraphemeCat.Prepend -> PairResult.Extended // GB9b
        after == GraphemeCat.InCbConsonant -> PairResult.InCbConsonant // GB9c
        before == GraphemeCat.Zwj &&
            after == GraphemeCat.ExtendedPictographic -> PairResult.Emoji // GB11
        before == GraphemeCat.RegionalIndicator &&
            after == GraphemeCat.RegionalIndicator -> PairResult.Regional // GB12, GB13
        else -> PairResult.Break // GB999
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
    private var catBefore: GraphemeCat? = null
    private var catAfter: GraphemeCat? = null
    private var preContextOffset: Int? = null
    private var incbLinkerCount: Int? = null
    private var risCount: Int? = null
    private var resuming: Boolean = false
    private var cacheLower: Int = 0
    private var cacheUpper: Int = 0
    private var cacheCat: GraphemeCat = GraphemeCat.Control

    private fun graphemeCategory(codePoint: Int): GraphemeCat {
        if (codePoint <= 0x7E) {
            return when {
                codePoint >= 0x20 -> GraphemeCat.Any
                codePoint == 0x0A -> GraphemeCat.Lf
                codePoint == 0x0D -> GraphemeCat.Cr
                else -> GraphemeCat.Control
            }
        }
        if (codePoint < cacheLower || codePoint > cacheUpper) {
            val res = io.github.kotlinmania.unicodesegmentation.graphemeCategory(codePoint)
            cacheLower = res.lower
            cacheUpper = res.upper
            cacheCat = res.category
        }
        return cacheCat
    }

    /** Set the cursor to a new location in the same string. */
    fun setCursor(offset: Int) {
        if (offset != this.offset) {
            this.offset = offset
            state = if (offset == 0 || offset == len) GraphemeState.Break else GraphemeState.Unknown
            catBefore = null
            catAfter = null
            incbLinkerCount = null
            risCount = null
        }
    }

    /** The current offset of the cursor. */
    fun curCursor(): Int = offset

    /** Provide additional pre-context when it is needed to decide a boundary. */
    fun provideContext(chunk: String, chunkStart: Int) {
        val required = preContextOffset
            ?: throw IllegalStateException("provideContext called when no pre-context was requested")
        require(chunkStart + chunk.length == required) {
            "chunk does not end at requested pre-context offset $required"
        }
        preContextOffset = null
        if (isExtended && chunkStart + chunk.length == offset) {
            val span = chunk.previousCodePointSpan(chunk.length)
            if (span != null && graphemeCategory(span.codePoint) == GraphemeCat.Prepend) {
                decide(false) // GB9b
                return
            }
        }
        when (state) {
            GraphemeState.InCbConsonant -> handleIncbConsonant(chunk, chunkStart)
            GraphemeState.Regional -> handleRegional(chunk, chunkStart)
            GraphemeState.Emoji -> handleEmoji(chunk, chunkStart)
            else -> {
                if (catBefore == null && offset == chunk.length + chunkStart) {
                    val span = chunk.previousCodePointSpan(chunk.length)
                    if (span != null) {
                        catBefore = graphemeCategory(span.codePoint)
                    }
                }
            }
        }
    }

    private fun decide(isBreak: Boolean) {
        state = if (isBreak) GraphemeState.Break else GraphemeState.NotBreak
    }

    private fun decision(isBreak: Boolean): Result<Boolean> {
        decide(isBreak)
        return Result.success(isBreak)
    }

    private fun isBoundaryResult(): Result<Boolean> =
        when (state) {
            GraphemeState.Break -> Result.success(true)
            GraphemeState.NotBreak -> Result.success(false)
            else -> {
                val preContext = preContextOffset
                if (preContext != null) {
                    Result.failure(GraphemeIncomplete.PreContext(preContext))
                } else {
                    error("inconsistent state")
                }
            }
        }

    private fun handleIncbConsonant(chunk: String, chunkStart: Int) {
        if (!isExtended) {
            decide(true)
            return
        }
        var count = incbLinkerCount ?: 0
        var index = chunk.length
        while (index > 0) {
            val span = chunk.previousCodePointSpan(index) ?: break
            index = span.start
            val codePoint = span.codePoint
            when {
                isIncbLinker(codePoint) -> {
                    count += 1
                    incbLinkerCount = count
                }
                DerivedProperty.incbExtend(codePoint) -> Unit
                else -> {
                    val result = !((incbLinkerCount ?: 0) > 0 && graphemeCategory(codePoint) == GraphemeCat.InCbConsonant)
                    decide(result)
                    return
                }
            }
        }
        if (chunkStart == 0) {
            decide(true)
        } else {
            preContextOffset = chunkStart
            state = GraphemeState.InCbConsonant
        }
    }

    private fun handleRegional(chunk: String, chunkStart: Int) {
        var count = risCount ?: 0
        var index = chunk.length
        while (index > 0) {
            val span = chunk.previousCodePointSpan(index) ?: break
            index = span.start
            if (graphemeCategory(span.codePoint) != GraphemeCat.RegionalIndicator) {
                risCount = count
                decide(count % 2 == 0)
                return
            }
            count += 1
        }
        risCount = count
        if (chunkStart == 0) {
            decide(count % 2 == 0)
        } else {
            preContextOffset = chunkStart
            state = GraphemeState.Regional
        }
    }

    private fun handleEmoji(chunk: String, chunkStart: Int) {
        var index = chunk.length
        val lastSpan = chunk.previousCodePointSpan(index)
        if (lastSpan != null) {
            if (graphemeCategory(lastSpan.codePoint) != GraphemeCat.Zwj) {
                decide(true)
                return
            }
            index = lastSpan.start
        }
        while (index > 0) {
            val span = chunk.previousCodePointSpan(index) ?: break
            index = span.start
            when (graphemeCategory(span.codePoint)) {
                GraphemeCat.Extend -> Unit
                GraphemeCat.ExtendedPictographic -> {
                    decide(false)
                    return
                }
                else -> {
                    decide(true)
                    return
                }
            }
        }
        if (chunkStart == 0) {
            decide(true)
        } else {
            preContextOffset = chunkStart
            state = GraphemeState.Emoji
        }
    }

    /** Determine whether the current cursor location is a grapheme cluster boundary. */
    fun isBoundary(chunk: String, chunkStart: Int): Result<Boolean> {
        if (state == GraphemeState.Break) return Result.success(true)
        if (state == GraphemeState.NotBreak) return Result.success(false)
        if ((offset < chunkStart || offset >= chunkStart + chunk.length) &&
            (offset > chunkStart + chunk.length || catAfter == null)
        ) {
            return Result.failure(GraphemeIncomplete.InvalidOffset)
        }
        val preContext = preContextOffset
        if (preContext != null) {
            return Result.failure(GraphemeIncomplete.PreContext(preContext))
        }
        val offsetInChunk = (offset - chunkStart).coerceAtLeast(0)
        if (catAfter == null) {
            val span = chunk.nextCodePointSpan(offsetInChunk)
                ?: return Result.failure(GraphemeIncomplete.InvalidOffset)
            catAfter = graphemeCategory(span.codePoint)
        }
        if (offset == chunkStart) {
            var needPreContext = true
            when (catAfter) {
                GraphemeCat.InCbConsonant -> state = GraphemeState.InCbConsonant
                GraphemeCat.RegionalIndicator -> state = GraphemeState.Regional
                GraphemeCat.ExtendedPictographic -> state = GraphemeState.Emoji
                else -> needPreContext = (catBefore == null)
            }
            if (needPreContext) {
                preContextOffset = chunkStart
                return Result.failure(GraphemeIncomplete.PreContext(chunkStart))
            }
        }
        if (catBefore == null) {
            val span = chunk.previousCodePointSpan(offsetInChunk)
                ?: return Result.failure(GraphemeIncomplete.InvalidOffset)
            catBefore = graphemeCategory(span.codePoint)
        }
        val before = catBefore!!
        val after = catAfter!!
        return when (checkPair(before, after)) {
            PairResult.NotBreak -> decision(false)
            PairResult.Break -> decision(true)
            PairResult.Extended -> decision(!isExtended)
            PairResult.InCbConsonant -> {
                handleIncbConsonant(chunk.substring(0, offsetInChunk), chunkStart)
                isBoundaryResult()
            }
            PairResult.Regional -> {
                val ris = risCount
                if (ris != null) {
                    return decision(ris % 2 == 0)
                }
                handleRegional(chunk.substring(0, offsetInChunk), chunkStart)
                isBoundaryResult()
            }
            PairResult.Emoji -> {
                handleEmoji(chunk.substring(0, offsetInChunk), chunkStart)
                isBoundaryResult()
            }
        }
    }

    /** Find the next boundary after the current cursor position. */
    fun nextBoundary(chunk: String, chunkStart: Int): Result<Int?> {
        if (offset == len) return Result.success(null)
        val offsetInChunk = (offset - chunkStart).coerceAtLeast(0)
        var nextSpan = chunk.nextCodePointSpan(offsetInChunk)
            ?: return Result.failure(GraphemeIncomplete.NextChunk)
        while (true) {
            if (resuming) {
                if (catAfter == null) {
                    catAfter = graphemeCategory(nextSpan.codePoint)
                }
            } else {
                offset = (offset + (nextSpan.end - nextSpan.start)).coerceAtMost(len)
                state = GraphemeState.Unknown
                catBefore = catAfter
                catAfter = null
                if (catBefore == null) {
                    catBefore = graphemeCategory(nextSpan.codePoint)
                }
                if (isIncbLinker(nextSpan.codePoint)) {
                    incbLinkerCount = (incbLinkerCount ?: 0) + 1
                } else if (!DerivedProperty.incbExtend(nextSpan.codePoint)) {
                    incbLinkerCount = 0
                }
                if (catBefore == GraphemeCat.RegionalIndicator) {
                    risCount = (risCount ?: 0) + 1
                } else {
                    risCount = 0
                }
                val followingSpan = chunk.nextCodePointSpan(nextSpan.end)
                if (followingSpan != null) {
                    nextSpan = followingSpan
                    catAfter = graphemeCategory(nextSpan.codePoint)
                } else if (offset == len) {
                    decide(true)
                } else {
                    resuming = true
                    return Result.failure(GraphemeIncomplete.NextChunk)
                }
            }
            resuming = true
            val boundaryRes = isBoundary(chunk, chunkStart)
            if (boundaryRes.isFailure) return Result.failure(boundaryRes.exceptionOrNull()!!)
            if (boundaryRes.getOrNull() == true) {
                resuming = false
                return Result.success(offset)
            }
            resuming = false
        }
    }

    /** Find the previous boundary before the current cursor position. */
    fun prevBoundary(chunk: String, chunkStart: Int): Result<Int?> {
        if (offset == 0) return Result.success(null)
        if (offset == chunkStart) return Result.failure(GraphemeIncomplete.PrevChunk)
        val offsetInChunk = (offset - chunkStart).coerceAtLeast(0)
        var prevSpan = chunk.previousCodePointSpan(offsetInChunk)
            ?: return Result.failure(GraphemeIncomplete.PrevChunk)
        while (true) {
            if (offset == chunkStart) {
                resuming = true
                return Result.failure(GraphemeIncomplete.PrevChunk)
            }
            if (resuming) {
                catBefore = graphemeCategory(prevSpan.codePoint)
            } else {
                offset -= (prevSpan.end - prevSpan.start)
                catAfter = catBefore
                catBefore = null
                state = GraphemeState.Unknown
                val linker = incbLinkerCount
                if (linker != null) {
                    incbLinkerCount = when {
                        linker > 0 && isIncbLinker(prevSpan.codePoint) -> linker - 1
                        DerivedProperty.incbExtend(prevSpan.codePoint) -> linker
                        else -> null
                    }
                }
                val ris = risCount
                if (ris != null) {
                    risCount = if (ris > 0) ris - 1 else null
                }
                val priorSpan = chunk.previousCodePointSpan(prevSpan.start)
                if (priorSpan != null) {
                    prevSpan = priorSpan
                    catBefore = graphemeCategory(prevSpan.codePoint)
                } else if (offset == 0) {
                    decide(true)
                } else {
                    resuming = true
                    catAfter = graphemeCategory(prevSpan.codePoint)
                    return Result.failure(GraphemeIncomplete.PrevChunk)
                }
            }
            resuming = true
            val boundaryRes = isBoundary(chunk, chunkStart)
            if (boundaryRes.isFailure) return Result.failure(boundaryRes.exceptionOrNull()!!)
            if (boundaryRes.getOrNull() == true) {
                resuming = false
                return Result.success(offset)
            }
            resuming = false
        }
    }
}

/** An error return indicating that not enough content was available in the provided chunk. */
sealed class GraphemeIncomplete(
    message: String,
) : RuntimeException(message) {
    /** More pre-context is needed. */
    data class PreContext(
        val offset: Int,
    ) : GraphemeIncomplete("more pre-context is needed")

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
    val codePoints =
        string
            .codePointSpans()
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
            (
                after == GraphemeCat.L ||
                    after == GraphemeCat.V ||
                    after == GraphemeCat.Lv ||
                    after == GraphemeCat.LvT
            ) -> false
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
            else ->
                return linkerCount > 0 &&
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
