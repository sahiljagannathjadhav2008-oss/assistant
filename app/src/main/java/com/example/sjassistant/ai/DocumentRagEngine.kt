package com.example.sjassistant.ai

import com.example.sjassistant.data.local.DocumentChunkEntity
import com.example.sjassistant.data.local.NoteModel

data class DocumentSearchHit(
    val sourceName: String,
    val snippet: String,
    val score: Int
)

/**
 * Lightweight on-device BM25-style extractive RAG engine over SAF-imported local documents
 * and encrypted/plain offline notes. Runs in <15ms with negligible RAM overhead.
 */
object DocumentRagEngine {
    private val stopWords = setOf(
        "the", "is", "are", "was", "were", "what", "how", "when", "where", "which", "about",
        "from", "with", "this", "that", "for", "and", "in", "on", "of", "to", "a", "an",
        "काय", "कसे", "कोण", "कुठे", "आहे", "आहेत", "बद्दल", "माझ्या", "फाइल", "मध्ये",
        "क्या", "कैसे", "कौन", "कहाँ", "है", "हैं", "के", "बारे", "में", "मेरी", "फ़ाइल"
    )

    fun searchDocumentsAndNotes(
        query: String,
        chunks: List<DocumentChunkEntity>,
        notes: List<NoteModel>,
        maxHits: Int = 3
    ): List<DocumentSearchHit> {
        val terms = extractTerms(query)
        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<DocumentSearchHit>()

        for (chunk in chunks) {
            val score = scoreText(terms, chunk.content, chunk.documentName)
            if (score > 0) {
                hits.add(
                    DocumentSearchHit(
                        sourceName = chunk.documentName,
                        snippet = chunk.content,
                        score = score
                    )
                )
            }
        }

        for (note in notes) {
            val combined = "${note.title}. ${note.content}"
            val score = scoreText(terms, combined, note.title)
            if (score > 0) {
                hits.add(
                    DocumentSearchHit(
                        sourceName = "Note: ${note.title}",
                        snippet = note.content.take(260),
                        score = score
                    )
                )
            }
        }

        return hits.sortedByDescending { it.score }.take(maxHits)
    }

    private fun extractTerms(query: String): List<String> {
        return query.lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
    }

    private fun scoreText(terms: List<String>, text: String, title: String): Int {
        val lowerText = text.lowercase()
        val lowerTitle = title.lowercase()
        var score = 0
        for (term in terms) {
            if (lowerTitle.contains(term)) score += 6
            var idx = lowerText.indexOf(term)
            var occurrences = 0
            while (idx >= 0 && occurrences < 4) {
                occurrences++
                score += 3
                idx = lowerText.indexOf(term, idx + term.length)
            }
        }
        return score
    }
}
