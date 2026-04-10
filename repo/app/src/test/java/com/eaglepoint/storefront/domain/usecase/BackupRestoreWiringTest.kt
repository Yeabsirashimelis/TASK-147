package com.eaglepoint.storefront.domain.usecase

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BackupRestoreWiringTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var reAuthenticate: ReAuthenticateUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var roleGuard: RoleGuard
    private lateinit var backupUseCase: BackupUseCase
    private lateinit var restoreUseCase: RestoreUseCase

    private val testUri = mockk<Uri>()
    private val metadataUri = mockk<Uri>()
    private val testMetadata = BackupMetadata(
        dbVersion = 8, timestamp = System.currentTimeMillis(),
        checksum = "sha256checksum123", appVersion = "1.0.0"
    )

    @BeforeEach
    fun setUp() {
        backupRepository = mockk(relaxed = true)
        reAuthenticate = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        roleGuard = mockk(relaxed = true)

        backupUseCase = BackupUseCase(backupRepository, reAuthenticate, logAuditEvent, roleGuard)
        restoreUseCase = RestoreUseCase(backupRepository, reAuthenticate, logAuditEvent, roleGuard)
    }

    // Backup wiring outcomes

    @Test
    fun `backup succeeds end-to-end with re-auth and creates metadata`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } returns testMetadata

        val result = backupUseCase("admin-1", "Pass1234".toCharArray(), testUri)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.checksum).isEqualTo("sha256checksum123")
        coVerify { logAuditEvent(userId = "admin-1", action = AuditAction.BACKUP_INITIATED, target = "backup", targetId = null, detail = null) }
        coVerify { logAuditEvent(userId = "admin-1", action = AuditAction.BACKUP_COMPLETED, target = "backup", targetId = null, detail = any()) }
    }

    @Test
    fun `backup fails when re-auth fails`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = backupUseCase("admin-1", "Wrong123".toCharArray(), testUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        coVerify(exactly = 0) { backupRepository.createBackup(any()) }
    }

    @Test
    fun `backup reports repository failure to user`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } throws RuntimeException("Disk full")

        val result = backupUseCase("admin-1", "Pass1234".toCharArray(), testUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Disk full")
    }

    // Restore wiring outcomes

    @Test
    fun `restore succeeds end-to-end with verify + re-auth + confirm`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns true

        val result = restoreUseCase("admin-1", "Pass1234".toCharArray(), testUri, metadataUri)

        assertThat(result.isSuccess).isTrue()
        coVerify { backupRepository.restoreBackup(testUri) }
        coVerify { logAuditEvent(userId = "admin-1", action = AuditAction.RESTORE_INITIATED, target = "backup", targetId = null, detail = any()) }
        coVerify { logAuditEvent(userId = "admin-1", action = AuditAction.RESTORE_COMPLETED, target = "backup", targetId = null, detail = null) }
    }

    @Test
    fun `restore fails when checksum mismatch`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns false

        val result = restoreUseCase("admin-1", "Pass1234".toCharArray(), testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("checksum")
        coVerify(exactly = 0) { backupRepository.restoreBackup(any()) }
    }

    @Test
    fun `restore fails when re-auth fails`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = restoreUseCase("admin-1", "Wrong123".toCharArray(), testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { backupRepository.restoreBackup(any()) }
    }

    @Test
    fun `verifyBackup returns metadata for valid backup`() = runTest {
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns true

        val result = restoreUseCase.verifyBackup(testUri, metadataUri)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.dbVersion).isEqualTo(8)
    }

    @Test
    fun `verifyBackup rejects tampered backup`() = runTest {
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns false

        val result = restoreUseCase.verifyBackup(testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
    }
}
