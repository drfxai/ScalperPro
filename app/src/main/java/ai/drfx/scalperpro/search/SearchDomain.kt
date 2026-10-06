package ai.drfx.scalperpro.search

enum class SearchCategory {
    TRADERPEDIA,
    STRATEGY,
    SIGNAL,
    NEWS,
    ECONOMIC_EVENT,
    JOURNAL,
    PINE_SCRIPT,
    MQL5_PROJECT,
    MODULE
}

data class SearchDocument(
    val id: String,
    val title: String,
    val body: String,
    val category: SearchCategory,
    val source: String,
    val destination: String?
)

data class SearchResult(
    val document: SearchDocument,
    val score: Int
)

class LocalSearchIndex(
    documents: List<SearchDocument>
) {
    private val indexed = documents.distinctBy { it.id }

    fun search(
        query: String,
        limit: Int = 50
    ): List<SearchResult> {
        require(limit in 1..200)

        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()

        return indexed
            .mapNotNull { document ->
                val title = normalize(document.title)
                val body = normalize(document.body)
                val source = normalize(document.source)

                var score = 0
                for (term in terms) {
                    if (term in title) score += 5
                    if (term in body) score += 2
                    if (term in source) score += 1
                }

                if (score > 0) {
                    SearchResult(document = document, score = score)
                } else {
                    null
                }
            }
            .sortedWith(
                compareByDescending<SearchResult> { it.score }
                    .thenBy { it.document.title }
            )
            .take(limit)
    }

    private fun tokenize(value: String): List<String> =
        normalize(value)
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .distinct()

    private fun normalize(value: String): String =
        value.trim().lowercase()
}
