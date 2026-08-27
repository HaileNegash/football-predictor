package com.example.network

/**
 * Extracts the first complete JSON object from model output.
 *
 * The previous implementation was `substringAfter("{").substringBeforeLast("}")`
 * re-wrapped in braces. That mangles any nested object, breaks on a `}` inside a
 * string, and — because `substringAfter` returns the whole receiver when the
 * delimiter is absent — silently produced garbage rather than failing when there
 * was no JSON at all.
 *
 * This does a proper brace scan that tracks string state and escapes.
 */
object JsonExtractor {

    fun extractObject(raw: String): String? {
        val text = stripFences(raw)
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        // Unbalanced — truncated response (usually a max_tokens cutoff).
        return null
    }

    /** Removes ```json fences some models emit despite instructions. */
    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
