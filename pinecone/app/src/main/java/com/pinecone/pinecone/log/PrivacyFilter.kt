package com.pinecone.pinecone.log

/**
 * Applies privacy rules before uploading events to server.
 *
 * Privacy is enforced at the schema level — fields like item title,
 * full URL, and search keywords are never stored in LogEvent types.
 * This filter is a pass-through that exists as an explicit extension
 * point for future server-side filtering needs.
 */
object PrivacyFilter {

    fun sanitize(event: LogEvent): LogEvent = event

    fun sanitizeBatch(events: List<LogEvent>): List<LogEvent> =
        events.map { sanitize(it) }
}
