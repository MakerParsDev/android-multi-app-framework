package com.parsfilo.contentapp.core.datastore

import org.json.JSONObject

/**
 * The latest recoverable count, including a reached target awaiting history persistence.
 * No prayer, advertising, or other preferences are affected by this snapshot.
 */
data class ActiveZikirSessionSnapshot(
    val zikirKey: String,
    val sessionId: Long,
    val count: Int,
    val target: Int,
    val startedAt: Long,
    val updatedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("version", 1)
        .put("key", zikirKey)
        .put("sessionId", sessionId)
        .put("count", count)
        .put("target", target)
        .put("startedAt", startedAt)
        .put("updatedAt", updatedAt)
        .toString()

    companion object {
        fun fromJson(raw: String): ActiveZikirSessionSnapshot? = runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version") != 1) return@runCatching null
            val value = ActiveZikirSessionSnapshot(
                zikirKey = json.getString("key"),
                sessionId = json.getLong("sessionId"),
                count = json.getInt("count"),
                target = json.getInt("target"),
                startedAt = json.getLong("startedAt"),
                updatedAt = json.getLong("updatedAt"),
            )
            value.takeIf {
                it.zikirKey.isNotBlank() &&
                    it.sessionId > 0L &&
                    it.count in 1..1_000_000 &&
                    it.target in 1..1_000_000 &&
                    it.startedAt > 0 &&
                    it.updatedAt >= it.startedAt
            }
        }.getOrNull()
    }
}
