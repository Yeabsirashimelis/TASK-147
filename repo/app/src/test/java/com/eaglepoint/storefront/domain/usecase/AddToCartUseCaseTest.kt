package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.domain.model.CatalogItem
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AddToCartUseCaseTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var addToCartUseCase: AddToCartUseCase

    private val testItem = CatalogItem(
        id = "item-1", sku = "SKU-001", name = "Jersey",
        price = 49.99, isActive = true
    )

    @BeforeEach
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)
        addToCartUseCase = AddToCartUseCase(cartRepository, catalogRepository)
    }

    @Test
    fun `adds item to cart when inventory available`() = runTest {
        coEvery { catalogRepository.findItemById("item-1") } returns testItem
        coEvery { catalogRepository.getAvailableQuantity("item-1") } returns 10

        val result = addToCartUseCase("cart-1", "item-1", 2)

        assertThat(result.isSuccess).isTrue()
        coVerify { cartRepository.addItem("cart-1", any()) }
    }

    @Test
    fun `fails when item not found`() = runTest {
        coEvery { catalogRepository.findItemById("missing") } returns null

        val result = addToCartUseCase("cart-1", "missing")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("not found")
    }

    @Test
    fun `fails when item inactive`() = runTest {
        coEvery { catalogRepository.findItemById("item-1") } returns testItem.copy(isActive = false)

        val result = addToCartUseCase("cart-1", "item-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("no longer available")
    }

    @Test
    fun `fails when insufficient inventory`() = runTest {
        coEvery { catalogRepository.findItemById("item-1") } returns testItem
        coEvery { catalogRepository.getAvailableQuantity("item-1") } returns 1

        val result = addToCartUseCase("cart-1", "item-1", 5)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Insufficient inventory")
    }

    @Test
    fun `fails when quantity less than 1`() = runTest {
        val result = addToCartUseCase("cart-1", "item-1", 0)

        assertThat(result.isFailure).isTrue()
    }
}
