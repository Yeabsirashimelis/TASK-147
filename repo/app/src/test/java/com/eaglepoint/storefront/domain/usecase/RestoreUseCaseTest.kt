package com.eaglepoint.storefront.domain.usecase

import android.net.Uri
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.domain.model.BackupMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestoreUseCaseTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var reAuthenticate: ReAuthenticateUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var roleGuard: RoleGuard
    private lateinit var restoreUseCase: RestoreUseCase

    private val sourceUri = mockk<Uri>()
    private val metadataUri = mockk<Uri>()
    private val testMetadata = BackupMetadata(
        dbVersion = 1,
        timestamp = System.currentTimeMillis(),
        checksum = "validchecksum123",
        appVersion = "1.0.0"
    )

    @BeforeEach
    fun setUp() {
        backupRepository = mockk(relaxed = true)
        reAuthenticate = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        roleGuard = mockk(relaxed = true)

        restoreUseCase = RestoreUseCase(backupRepository, reAuthenticate, logAuditEvent, roleGuard)
    }

    @Test
    fun `restore requires re-authentication`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns false

        val result = restoreUseCase("user-id", "Password1".toCharArray(), sourceUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        coVerify(exactly = 0) { backupRepository.restoreBackup(any()) }
    }

    @Test
    fun `restore aborts on checksum mismatch`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(sourceUri, testMetadata.checksum) } returns false

        val result = restoreUseCase("user-id", "Password1".toCharArray(), sourceUri, metadataUri)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("checksum")
        coVerify(exactly = 0) { backupRepository.restoreBackup(any()) }
    }

    @Test
    fun `restore succeeds with valid checksum and re-auth`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(sourceUri, testMetadata.checksum) } returns true

        val result = restoreUseCase("user-id", "Password1".toCharArray(), sourceUri, metadataUri)

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { backupRepository.restoreBackup(sourceUri) }
    }

    @Test
    fun `restore fails when metadata cannot be read`() = runTest {
        coEvery { reAuthenticate(any(), any()) } returns true
        coEvery { backupRepository.readMetadata(metadataUri) } returns null

        val result = restoreUseCase("user-id", "Password1".toCharArray(), sourceUri, metadataUri)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `verifyBackup returns metadata on valid backup`() = runTest {
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(sourceUri, testMetadata.checksum) } returns true

        val result = restoreUseCase.verifyBackup(sourceUri, metadataUri)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(testMetadata)
    }

    @Test
    fun `verifyBackup fails on checksum mismatch`() = runTest {
        coEvery { backupRepository.readMetadata(metadataUri) } returns testMetadata
        coEvery { backupRepository.verifyBackup(sourceUri, testMetadata.checksum) } returns false

        val result = restoreUseCase.verifyBackup(sourceUri, metadataUri)

        assertThat(result.isFailure).isTrue()
    }
}
