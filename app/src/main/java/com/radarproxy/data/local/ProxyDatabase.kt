package com.radarproxy.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey val id: String, val type: String, val server: String, val port: Int,
    val secret: String?, val username: String?, val password: String?, val sourceId: String,
    val firstSeen: Long, val lastSeen: Long, val pingMs: Long?, val pingStatus: String, val lastPingTime: Long?
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String, val url: String, val official: Boolean, val enabled: Boolean,
    val online: Boolean?, val lastUpdated: Long?, val proxyCount: Int, val lastError: String?
)

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY lastSeen DESC") fun observeAll(): Flow<List<ProxyEntity>>
    @Query("SELECT * FROM proxies") suspend fun all(): List<ProxyEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<ProxyEntity>)
    @Query("DELETE FROM proxies WHERE sourceId=:sourceId") suspend fun deleteBySource(sourceId: String)
    @Query("DELETE FROM proxies WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
    @Query("DELETE FROM proxies WHERE lastSeen < :cutoff") suspend fun deleteOlderThan(cutoff: Long): Int
    @Query("UPDATE proxies SET pingMs=:ms,pingStatus=:status,lastPingTime=:time WHERE id=:id") suspend fun updatePing(id: String, ms: Long?, status: String, time: Long)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY official DESC, id") fun observeAll(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources WHERE enabled=1") suspend fun enabled(): List<SourceEntity>
    @Query("SELECT * FROM sources WHERE url=:url LIMIT 1") suspend fun findByUrl(url: String): SourceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(source: SourceEntity)
    @Query("DELETE FROM sources WHERE id=:id AND official=0") suspend fun deleteCustom(id: String)
}

@Database(entities = [ProxyEntity::class, SourceEntity::class], version = 1, exportSchema = false)
abstract class ProxyDatabase : RoomDatabase() {
    abstract fun proxies(): ProxyDao
    abstract fun sources(): SourceDao
}
