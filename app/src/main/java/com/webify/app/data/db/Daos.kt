package com.webify.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WebProjectDao {
    @Query("SELECT * FROM web_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WebProjectEntity>>

    @Query("SELECT * FROM web_projects WHERE id = :id")
    fun observe(id: String): Flow<WebProjectEntity?>

    @Query("SELECT * FROM web_projects WHERE id = :id")
    suspend fun get(id: String): WebProjectEntity?

    @Query("SELECT * FROM web_projects WHERE packageName = :pkg LIMIT 1")
    suspend fun findByPackage(pkg: String): WebProjectEntity?

    @Query("SELECT COUNT(*) FROM web_projects WHERE packageName = :pkg AND id != :excludeId")
    suspend fun countPackage(pkg: String, excludeId: String): Int

    @Upsert
    suspend fun upsert(project: WebProjectEntity)

    @Delete
    suspend fun delete(project: WebProjectEntity)

    @Query("UPDATE web_projects SET customCssCode = :css, updatedAt = :now WHERE id = :id")
    suspend fun updateCss(id: String, css: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE web_projects SET customJsOnPageFinished = :js, updatedAt = :now WHERE id = :id")
    suspend fun updateJsFinished(id: String, js: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE web_projects SET selectedKeystoreId = NULL WHERE selectedKeystoreId = :keystoreId")
    suspend fun clearKeystore(keystoreId: String)
}

@Dao
interface KeystoreDao {
    @Query("SELECT * FROM keystores ORDER BY isDebugDefault DESC, createdAt DESC")
    fun observeAll(): Flow<List<KeystoreEntity>>

    @Query("SELECT * FROM keystores WHERE id = :id")
    suspend fun get(id: String): KeystoreEntity?

    @Query("SELECT * FROM keystores WHERE isDebugDefault = 1 LIMIT 1")
    suspend fun getDebugDefault(): KeystoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: KeystoreEntity)

    @Delete
    suspend fun delete(entity: KeystoreEntity)
}

@Dao
interface BuildRecordDao {
    @Query("SELECT * FROM build_records WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun observeForProject(projectId: String): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BuildRecordEntity>>

    @Query("SELECT COUNT(*) FROM build_records WHERE status = 'SUCCESS'")
    fun observeSuccessCount(): Flow<Int>

    @Query("SELECT * FROM build_records WHERE id = :id")
    suspend fun get(id: String): BuildRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BuildRecordEntity)

    @Query("DELETE FROM build_records WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clear(conversationId: String)
}
