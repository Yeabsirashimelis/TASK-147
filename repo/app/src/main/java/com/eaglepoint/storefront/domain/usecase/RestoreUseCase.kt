package com.eaglepoint.storefront.domain.usecase

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.BackupMetadata

class RestoreUseCase(
    private val backupRepository: BackupRepository,
    private val reAuthenticate: ReAuthenticateUseCase,
    private val logAuditEvent: LogAuditEventUseCase,
    private val roleGuard: RoleGuard
) {

    suspend fun verifyBackup(sourceUri: Uri, metadataUri: Uri): Result<BackupMetadata> {
        val metadata = backupRepository.readMetadata(metadataUri)
            ?: return Result.failure(IllegalStateException("Cannot read backup metadata"))

        val checksumValid = backupRepository.verifyBackup(sourceUri, metadata.checksum)
        if (!checksumValid) {
            return Result.failure(SecurityException("Backup checksum verification failed"))
        }

        return Result.success(metadata)
    }

    suspend operator fun invoke(
        userId: String,
        password: CharArray,
        sourceUri: Uri,
        metadataUri: Uri
    ): Result<Unit> {
        roleGuard.enforceBackupRestoreAccess()

        // Step-up re-authentication required
        val reAuthResult = reAuthenticate(userId, password)
        if (!reAuthResult) {
            return Result.failure(SecurityException("Re-authentication failed"))
        }

        // Verify checksum integrity
        val metadata = backupRepository.readMetadata(metadataUri)
            ?: return Result.failure(IllegalStateException("Cannot read backup metadata"))

        val checksumValid = backupRepository.verifyBackup(sourceUri, metadata.checksum)
        if (!checksumValid) {
            return Result.failure(SecurityException("Backup integrity check failed — checksum mismatch"))
        }

        return try {
            logAuditEvent(
                userId = userId,
                action = AuditAction.RESTORE_INITIATED,
                target = "backup",
                detail = "Restoring from backup dated ${metadata.timestamp}"
            )

            backupRepository.restoreBackup(sourceUri)

            logAuditEvent(
                userId = userId,
                action = AuditAction.RESTORE_COMPLETED,
                target = "backup"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
