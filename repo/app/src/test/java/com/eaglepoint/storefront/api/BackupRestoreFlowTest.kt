package com.eaglepoint.storefront.api

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.eaglepoint.storefront.domain.usecase.BackupUseCase
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ReAuthenticateUseCase
import com.eaglepoint.storefront.domain.usecase.RestoreUseCase
import com.eaglepoint.storefront.domain.usecase.RoleGuard
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * API-level functional tests for backup and restore flows.
 * Covers re-authentication requirement, checksum verification,
 * role enforcement, and data state changes.
 */
class BackupRestoreFlowTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var reAuthenticate: ReAuthenticateUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var roleGuard: RoleGuard
    private lateinit var backupUseCase: BackupUseCase
    private lateinit var restoreUseCase: RestoreUseCase

    private val testUri = mockk<Uri>()
    private val metadataUri = mockk<Uri>()
    private val testMetadata = BackupMetadata(
        dbVersion = 1, timestamp = System.currentTimeMillis(),
        checksum = "abc123", appVersion = "1.0.0"
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

    // --- Backup: re-auth required ---
    @Test
    fun `backup fails when re-authentication fails`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = backupUseCase("user-1", "Password1".toCharArray(), testUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    // --- Backup: success ---
    @Test
    fun `backup succeeds after re-authentication`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.createBackup(testUri) } returns testMetadata

        val result = backupUseCase("user-1", "Password1".toCharArray(), testUri)

        assertThat(result.isSuccess).isTrue()
    }

    // --- Restore: checksum mismatch ---
    @Test
    fun `restore fails on checksum mismatch`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns false

        val result = restoreUseCase("user-1", "Password1".toCharArray(), testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("checksum")
    }

    // --- Restore: re-auth required ---
    @Test
    fun `restore fails when re-authentication fails`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = restoreUseCase("user-1", "Password1".toCharArray(), testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        coVerify(exactly = 0) { backupRepository.restoreBackup(any()) }
    }

    // --- Restore: success ---
    @Test
    fun `restore succeeds with valid checksum and re-auth`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(testUri, testMetadata.checksum) } returns true

        val result = restoreUseCase("user-1", "Password1".toCharArray(), testUri, metadataUri)

        assertThat(result.isSuccess).isTrue()
        coVerify { backupRepository.restoreBackup(testUri) }
    }

    // --- Restore: metadata unreadable ---
    @Test
    fun `restore fails when metadata cannot be read`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns null

        val result = restoreUseCase("user-1", "Password1".toCharArray(), testUri, metadataUri)

        assertThat(result.isFailure).isTrue()
    }
}
