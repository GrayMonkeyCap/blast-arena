package com.lifeledger.sms.parser.life

/**
 * Shared "first keyword in this table wins" matcher used by the life-event parsers to turn
 * a raw phrase (a courier's brand name, an airline, a lab chain) into the canonical label
 * the timeline shows. Table order matters: put more specific phrases before generic ones
 * that could otherwise shadow them.
 */
internal object KeywordTable {
    fun firstMatch(body: String, table: List<Pair<String, String>>): String? {
        val lower = body.lowercase()
        return table.firstOrNull { (keyword, _) -> lower.contains(keyword) }?.second
    }

    fun anyMatch(body: String, keywords: List<String>): Boolean {
        val lower = body.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
