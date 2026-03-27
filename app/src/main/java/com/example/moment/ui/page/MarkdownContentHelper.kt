package com.example.moment.ui.page

object MarkdownContentHelper {
    private val imageRegex = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
    private val markdownSymbolRegex = Regex("[#>*`_~\\[\\]()-]")

    fun appendImageSyntax(current: String, imageUri: String): String {
        val prefix = if (current.isBlank()) "" else if (current.endsWith("\n")) "" else "\n\n"
        return "$current$prefix![图片]($imageUri)\n"
    }

    fun extractFirstImageUri(markdown: String?, fallbackImageUri: String? = null): String? {
        val fromMarkdown = imageRegex.find(markdown.orEmpty())?.groupValues?.getOrNull(1)
        return fromMarkdown ?: fallbackImageUri
    }

    fun collectImageUris(markdown: String?, fallbackImageUri: String? = null): List<String> {
        val uris = imageRegex.findAll(markdown.orEmpty()).map { it.groupValues[1] }.toMutableList()
        if (!fallbackImageUri.isNullOrBlank() && uris.none { it == fallbackImageUri }) {
            uris.add(fallbackImageUri)
        }
        return uris
    }

    fun stripMarkdownToPlainText(markdown: String?): String? {
        if (markdown.isNullOrBlank()) return null
        val withoutImages = imageRegex.replace(markdown) { "" }
        val normalized = withoutImages
            .replace(markdownSymbolRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.ifBlank { null }
    }

    fun buildPreviewText(markdown: String?, fallbackText: String?, maxLen: Int): String? {
        val source = stripMarkdownToPlainText(markdown) ?: fallbackText?.trim().orEmpty()
        if (source.isBlank()) return null
        return source.take(maxLen)
    }
}

