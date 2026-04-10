package com.eaglepoint.storefront.domain.usecase

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.BackupMetadata

class BackupUseCase(
    private val backupRepository: BackupRepository,
    private val reAuthenticate: ReAuthenticateUseCase,
    private val logAuditEvent: LogAuditEventUseCase,
    private val roleGuard: RoleGuard
) {

    suspend operator fun invoke(
        userId: String,
        password: CharArray,
        destinationUri: Uri
    ): Result<BackupMetadata> {
        roleGuard.enforceBackupRestoreAccess()

        // Step-up re-authentication required
        val reAuthResult = reAuthenticate(userId, password)
        if (!reAuthResult) {
            return Result.failure(SecurityException("Re-authentication failed"))
        }

        return try {
            logAuditEvent(
                userId = userId,
                action = AuditAction.BACKUP_INITIATED,
                target = "backup"
            )

            val metadata = backupRepository.createBackup(destinationUri)

            logAuditEvent(
                userId = userId,
                action = AuditAction.BACKUP_COMPLETED,
                target = "backup",
                detail = "Checksum: ${metadata.checksum.take(8)}..."
            )

            Result.success(metadata)
        } catch (e: Exception) {
            logAuditEvent(
                userId = userId,
                action = AuditAction.BACKUP_INITIATED,
                target = "backup",
                detail = "Backup failed: ${e.message}"
            )
            Result.failure(e)
        }
    }
}
