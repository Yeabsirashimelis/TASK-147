package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MergeCartsUseCaseTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var mergeCartsUseCase: MergeCartsUseCase
    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        mergeCartsUseCase = MergeCartsUseCase(cartRepository, logAuditEvent)
    }

    @Test
    fun `merges guest cart into user cart`() = runTest {
        val mergedCart = Cart(
            id = "user-cart", userId = "user-1",
            items = listOf(
                CartLineItem(
                    id = "li-1", cartId = "user-cart", catalogItemId = "cat-1",
                    sku = "SKU-1", name = "Item", unitPrice = 10.0, quantity = 3,
                    createdAt = now, updatedAt = now
                )
            ),
            createdAt = now, updatedAt = now
        )
        coEvery { cartRepository.mergeGuestCartIntoUser("guest-cart", "user-1") } returns mergedCart

        val result = mergeCartsUseCase("guest-cart", "user-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.items).hasSize(1)
        coVerify { logAuditEvent(userId = "user-1", action = any(), target = "cart", targetId = "user-cart", detail = any()) }
    }

    @Test
    fun `returns failure on exception`() = runTest {
        coEvery { cartRepository.mergeGuestCartIntoUser(any(), any()) } throws RuntimeException("DB error")

        val result = mergeCartsUseCase("guest-cart", "user-1")

        assertThat(result.isFailure).isTrue()
    }
}
