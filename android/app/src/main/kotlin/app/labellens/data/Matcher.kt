package app.labellens.data

data class Match(
    val additive: Additive,
    /** The text on the label that produced the match, as read. */
    val readAs: String,
    /** Similarity for name matches, 0..100. Null for code matches. */
    val percent: Int? = null,
)

data class ScanResult(
    val text: String,
    val codeMatches: List<Match>,
    val nameMatches: List<Match>,
    val otherNumbers: List<String>,
    /** Character ranges in [text] to highlight. */
    val highlights: List<IntRange>,
    /** True when rebuilt from History, where the label text is not kept on the phone. */
    val fromHistory: Boolean = false,
) {
    val all get() = codeMatches + nameMatches
}

/** Finds additive codes and names in the text read from a label. Runs entirely on the phone. */
object Matcher {
    private val codePattern = Regex("\\b(?:E|INS)[\\s\\-.]{0,2}(\\d{3,4})\\s?([a-i])?\\b", RegexOption.IGNORE_CASE)
    private val wordPattern = Regex("[\\p{L}\\p{N}&'-]+")
    private val numberPattern = Regex("\\b\\d{3,4}\\b")
    private const val FUZZY_THRESHOLD = 0.85

    fun scan(text: String, catalogue: List<Additive>): ScanResult {
        val byCode = catalogue.associateBy { it.code.lowercase() }
        val codeMatches = LinkedHashMap<String, Match>()
        val highlights = mutableListOf<IntRange>()
        val usedNumbers = mutableSetOf<String>()

        for (m in codePattern.findAll(text)) {
            val digits = m.groupValues[1]
            val suffix = m.groupValues[2].lowercase()
            val additive = byCode["e$digits$suffix"] ?: byCode["e$digits"] ?: continue
            usedNumbers += digits
            highlights += m.range
            codeMatches.getOrPut(additive.code) { Match(additive, m.value.trim()) }
        }

        val words = wordPattern.findAll(text).toList()
        val nameMatches = LinkedHashMap<String, Match>()
        for (additive in catalogue) {
            if (additive.code in codeMatches) continue
            val candidates = (listOf(additive.name, additive.shortName) + additive.aliases).distinct()
            var best: Triple<Double, IntRange, String>? = null
            for (candidate in candidates) {
                val target = candidate.lowercase()
                val size = target.split(' ').size
                if (words.size < size) continue
                for (start in 0..words.size - size) {
                    val range = words[start].range.first..words[start + size - 1].range.last
                    val fragment = text.substring(range)
                    val score = similarity(fragment.lowercase(), target)
                    // Short names such as "MSG" must match exactly, or they match everything.
                    val enough = if (target.length < 6) score == 1.0 else score >= FUZZY_THRESHOLD
                    if (enough && (best == null || score > best.first)) best = Triple(score, range, fragment)
                }
            }
            best?.let { (score, range, fragment) ->
                nameMatches[additive.code] = Match(additive, fragment, (score * 100).toInt())
                highlights += range
            }
        }

        val otherNumbers = numberPattern.findAll(text).map { it.value }.filter { it !in usedNumbers }.distinct().take(6).toList()
        return ScanResult(text, codeMatches.values.toList(), nameMatches.values.toList(), otherNumbers, mergeRanges(highlights))
    }

    /** A result for a scan loaded from History, where only the codes were kept. */
    fun fromCodes(codes: List<String>, catalogue: List<Additive>): ScanResult {
        val byCode = catalogue.associateBy { it.code }
        return ScanResult("", codes.mapNotNull { byCode[it] }.map { Match(it, it.code) }, emptyList(), emptyList(), emptyList(), fromHistory = true)
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val merged = mutableListOf<IntRange>()
        for (r in ranges.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.size - 1] = last.first..maxOf(last.last, r.last) else merged += r
        }
        return merged
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        if (longest == 0 || kotlin.math.abs(a.length - b.length) > longest * (1 - FUZZY_THRESHOLD) + 1) return 0.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Search used by the search screen: codes ignore spaces and dashes, "INS 330" finds E330. */
    fun search(query: String, catalogue: List<Additive>): List<Additive> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return catalogue
        val compact = q.replace(Regex("[\\s\\-.]"), "")
        val asCode = compact.replace(Regex("^ins"), "e")
        return catalogue.filter { a ->
            val code = a.code.lowercase()
            code.contains(asCode) || code.removePrefix("e").startsWith(compact) ||
                a.name.lowercase().contains(q) || a.category.lowercase().contains(q) ||
                a.descriptor.lowercase().contains(q) || a.aliases.any { it.lowercase().contains(q) }
        }
    }
}
