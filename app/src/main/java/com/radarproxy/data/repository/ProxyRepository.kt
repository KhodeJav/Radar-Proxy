package com.radarproxy.data.repository

import androidx.room.withTransaction
import com.radarproxy.core.parser.ProxyParser
import com.radarproxy.data.local.*
import com.radarproxy.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProxyRepository(private val db: ProxyDatabase) {
    private val parser = ProxyParser()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val proxies: Flow<List<ProxyEntity>> = db.proxies().observeAll()
    val sources: Flow<List<SourceEntity>> = db.sources().observeAll()

    suspend fun refreshAll(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val enabled = db.sources().enabled()
            val oldEntities = db.proxies().all()
            val oldByCanonical = oldEntities.mapNotNull { entity -> entity.canonicalId()?.let { it to entity } }
                .toMap()
            val merged = LinkedHashMap<String, ProxyEntity>()
            var successful = 0
            val errors = mutableListOf<String>()

            enabled.forEach { source ->
                runCatching {
                    val request = Request.Builder()
                        .url(source.url)
                        .header("Accept", "text/plain")
                        .header("Cache-Control", "no-cache")
                        .build()
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = response.body?.string().orEmpty()
                        val parsed = parser.parse(body, source.id)
                        check(parsed.isNotEmpty()) { "Empty source" }
                        parsed.forEach { item ->
                            val previous = oldByCanonical[item.id]
                            val next = item.toEntity().copy(
                                pingMs = previous?.pingMs,
                                pingStatus = previous?.pingStatus ?: PingStatus.UNTESTED.name,
                                lastPingTime = previous?.lastPingTime,
                                firstSeen = previous?.firstSeen ?: item.firstSeen
                            )
                            // The canonical id is the primary key. This also removes
                            // duplicates that arrive through multiple source URLs.
                            merged[item.id] = merged[item.id]?.let { existing ->
                                if (existing.sourceId == "official" && !source.official) existing else next
                            } ?: next
                        }
                        db.sources().upsert(source.copy(online = true, lastUpdated = System.currentTimeMillis(), proxyCount = parsed.size, lastError = null))
                        successful++
                    }
                }.onFailure { error ->
                    val message = error.message ?: "Unable to fetch subscription"
                    errors += "${source.url}: $message"
                    db.sources().upsert(source.copy(online = false, lastError = message))
                }
            }

            check(successful > 0) { errors.joinToString(" | ").ifBlank { "No enabled subscription source succeeded" } }
            if (merged.isNotEmpty()) {
                val duplicateIds = oldEntities.mapNotNull { entity ->
                    val canonical = entity.canonicalId()
                    canonical?.takeIf { it in merged && entity.id != it }?.let { entity.id }
                }
                db.withTransaction {
                    if (duplicateIds.isNotEmpty()) db.proxies().deleteByIds(duplicateIds)
                    db.proxies().upsertAll(merged.values.toList())
                }
            }
            merged.size
        }
    }

    suspend fun addSource(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = url.trim()
            val uri = URI(normalized)
            require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "Invalid subscription URL" }
            require(uri.host != null) { "Invalid subscription URL" }
            require(uri.path.orEmpty().endsWith(".txt", ignoreCase = true)) { "Subscription URL must end with .txt" }
            require(db.sources().findByUrl(normalized) == null) { "Duplicate source" }
            db.sources().upsert(SourceEntity(UUID.randomUUID().toString(), normalized, false, true, null, null, 0, null))
        }
    }

    suspend fun sourceByUrl(url: String): SourceEntity? = withContext(Dispatchers.IO) {
        db.sources().findByUrl(url.trim())
    }

    suspend fun deleteSource(id: String) = withContext(Dispatchers.IO) {
        // Source removal is not proxy removal. Previously collected rows remain
        // visible until the user explicitly enables the age-based auto-cleanup.
        db.sources().deleteCustom(id)
    }

    suspend fun setPing(id: String, ms: Long?, status: PingStatus) = db.proxies().updatePing(id, ms, status.name, System.currentTimeMillis())

    private fun ProxyEntry.toEntity() = ProxyEntity(id, type.name, server, port, secret, username, password, sourceId, firstSeen, lastSeen, pingMs, pingStatus.name, lastPingTime)

    private fun ProxyEntity.canonicalId(): String? = runCatching {
        ProxyIdentity.stableId(ProxyType.valueOf(type), server, port, secret, username, password)
    }.getOrNull()
}
