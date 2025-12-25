package no.solver.solverapp.features.objects

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import no.solver.solverapp.MainDispatcherRule
import no.solver.solverapp.core.network.ApiException
import no.solver.solverapp.core.network.ApiResult
import no.solver.solverapp.data.models.SolverObject
import no.solver.solverapp.data.repositories.ObjectsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var objectsRepository: ObjectsRepository

    @Before
    fun setup() {
        objectsRepository = mockk(relaxed = true)
    }

    @Test
    fun `loads objects successfully and shows Success state`() = runTest {
        val objects = listOf(
            createTestObject(1, "Object A"),
            createTestObject(2, "Object B")
        )
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(objects)

        val viewModel = ObjectsViewModel(objectsRepository)

        val state = viewModel.uiState.value
        assertTrue("Expected Success state but got $state", state is ObjectsUiState.Success)
        assertEquals(2, (state as ObjectsUiState.Success).objects.size)
    }

    @Test
    fun `shows Empty state when no objects returned`() = runTest {
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(emptyList())

        val viewModel = ObjectsViewModel(objectsRepository)

        assertEquals(ObjectsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `shows Error state on API failure`() = runTest {
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Error(
            ApiException.Network("Network error")
        )

        val viewModel = ObjectsViewModel(objectsRepository)

        val state = viewModel.uiState.value
        assertTrue(state is ObjectsUiState.Error)
        assertEquals("Network error", (state as ObjectsUiState.Error).message)
    }

    @Test
    fun `statistics are calculated correctly`() = runTest {
        val objects = listOf(
            createTestObject(1, "Object 1", online = true, active = true),
            createTestObject(2, "Object 2", online = false, active = true),
            createTestObject(3, "Object 3", online = true, active = false),
            createTestObject(4, "Object 4", online = true, active = true)
        )
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(objects)

        val viewModel = ObjectsViewModel(objectsRepository)

        val stats = viewModel.statistics
        assertEquals(4, stats.total)
        assertEquals(3, stats.online)
        assertEquals(1, stats.offline)
        assertEquals(3, stats.active)
        assertEquals(1, stats.inactive)
        assertEquals(2, stats.available)
    }

    @Test
    fun `retry reloads objects`() = runTest {
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Error(
            ApiException.Network("Error")
        )

        val viewModel = ObjectsViewModel(objectsRepository)
        assertTrue(viewModel.uiState.value is ObjectsUiState.Error)

        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(
            listOf(createTestObject(1, "Object"))
        )

        viewModel.retry()

        assertTrue(viewModel.uiState.value is ObjectsUiState.Success)
    }

    @Test
    fun `refresh updates objects`() = runTest {
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(
            listOf(createTestObject(1, "Initial"))
        )

        val viewModel = ObjectsViewModel(objectsRepository)
        
        val initialState = viewModel.uiState.value as ObjectsUiState.Success
        assertEquals(1, initialState.objects.size)

        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(
            listOf(
                createTestObject(1, "Updated"),
                createTestObject(2, "New Object")
            )
        )

        viewModel.refreshObjects()

        val updatedState = viewModel.uiState.value as ObjectsUiState.Success
        assertEquals(2, updatedState.objects.size)
    }

    @Test
    fun `setSearchQuery updates query state`() = runTest {
        coEvery { objectsRepository.getUserObjects() } returns ApiResult.Success(emptyList())

        val viewModel = ObjectsViewModel(objectsRepository)
        
        assertEquals("", viewModel.searchQuery.value)
        
        viewModel.setSearchQuery("test")
        
        assertEquals("test", viewModel.searchQuery.value)
    }

    private fun createTestObject(
        id: Int,
        name: String,
        online: Boolean = true,
        active: Boolean = true
    ) = SolverObject(
        id = id,
        name = name,
        objectTypeId = 1,
        status = "Available",
        online = online,
        active = active
    )
}
