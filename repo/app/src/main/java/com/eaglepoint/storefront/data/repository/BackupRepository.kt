package com.eaglepoint.storefront.data.repository

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import com.eaglepoint.storefront.data.db.StorefrontDatabase
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.eaglepoint.storefront.security.ChecksumUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class BackupRepository(
    private val database: StorefrontDatabase,
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun createBackup(destinationUri: Uri): BackupMetadata = withContext(dispatcher) {
        // Checkpoint WAL to ensure all data is in the main DB file
        database.auditEventDao().checkpoint(
            SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")
        )

        val dbFile = context.getDatabasePath(StorefrontDatabase.DATABASE_NAME)

        // Copy DB to destination
        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            dbFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Cannot open output stream for backup destination")

        val checksum = ChecksumUtil.computeSha256(dbFile)
        val metadata = BackupMetadata(
            dbVersion = DATABASE_VERSION,
            timestamp = System.currentTimeMillis(),
            checksum = checksum,
            appVersion = APP_VERSION
        )

        // Write metadata sidecar
        val metadataJson = JSONObject().apply {
            put("dbVersion", metadata.dbVersion)
            put("timestamp", metadata.timestamp)
            put("checksum", metadata.checksum)
            put("appVersion", metadata.appVersion)
        }

        val metadataUri = Uri.parse("$destinationUri.meta.json")
        context.contentResolver.openOutputStream(metadataUri)?.use { outputStream ->
            outputStream.write(metadataJson.toString().toByteArray(Charsets.UTF_8))
        }

        metadata
    }

    suspend fun verifyBackup(sourceUri: Uri, expectedChecksum: String): Boolean =
        withContext(dispatcher) {
            val actualChecksum = context.contentResolver.openInputStream(sourceUri)?.use {
                ChecksumUtil.computeSha256(it)
            } ?: return@withContext false

            actualChecksum.equals(expectedChecksum, ignoreCase = true)
        }

    suspend fun readMetadata(metadataUri: Uri): BackupMetadata? = withContext(dispatcher) {
        try {
            val jsonString = context.contentResolver.openInputStream(metadataUri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext null

            val json = JSONObject(jsonString)
            BackupMetadata(
                dbVersion = json.getInt("dbVersion"),
                timestamp = json.getLong("timestamp"),
                checksum = json.getString("checksum"),
                appVersion = json.getString("appVersion")
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreBackup(sourceUri: Uri) = withContext(dispatcher) {
        database.close()

        val dbFile = context.getDatabasePath(StorefrontDatabase.DATABASE_NAME)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")

        // Remove WAL and SHM files before overwriting
        walFile.delete()
        shmFile.delete()

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            dbFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IllegalStateException("Cannot open input stream for backup source")
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val APP_VERSION = "1.0.0"
    }
}
