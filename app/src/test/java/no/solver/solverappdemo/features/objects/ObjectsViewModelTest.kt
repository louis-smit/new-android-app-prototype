package no.solver.solverappdemo.features.objects

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import no.solver.solverappdemo.MainDispatcherRule
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.config.DataSimulationEvent
import no.solver.solverappdemo.core.config.DebugConfigurationManager
import no.solver.solverappdemo.core.network.ConnectivityObserver
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.core.network.NetworkStatus
import no.solver.solverappdemo.core.storage.FavouritesStore
import no.solver.solverappdemo.core.cache.IconCacheManager
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.data.repositories.ObjectsLoadResult
import no.solver.solverappdemo.data.repositories.OfflineFirstObjectsRepository
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var offlineFirstRepository: OfflineFirstObjectsRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var favouritesStore: FavouritesStore
    private lateinit var iconCacheManager: IconCacheManager
    private lateinit var debugConfigManager: DebugConfigurationManager

    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Available)
    private val favouritesFlow = MutableStateFlow<List<SolverObject>>(emptyList())
    private val simulationEventsFlow = MutableSharedFlow<DataSimulationEvent>()

    private val testSession = Session(
        id = "test-session",
        provider = AuthProvider.MICROSOFT,
        environment = AuthEnvironment.SOLVER,
        tokens = AuthTokens(
            accessToken = "test-token",
            refreshToken = null,
            expiresAtMillis = System.currentTimeMillis() + 3600_000
        ),
        isActive = true
    )

    @Before
    fun setup() {
        offlineFirstRepository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        connectivityObserver = mockk(relaxed = true)
        favouritesStore = mockk(relaxed = true)
        iconCacheManager = mockk(relaxed = true)
        debugConfigManager = mockk(relaxed = true)

        sessionFlow.value = testSession
        every { sessionManager.currentSessionFlow } returns sessionFlow

        every { connectivityObserver.networkStatus } returns networkStatusFlow
        every { connectivityObserver.isConnected() } returns true

        every { favouritesStore.favourites } returns favouritesFlow
        coEvery { favouritesStore.loadFavourites() } returns ApiResult.Success(emptyList())

        every { debugConfigManager.simulationEvents } returns simulationEventsFlow
    }

    private fun createViewModel(): ObjectsViewModel {
        return ObjectsViewModel(
            offlineFirstRepository = offlineFirstRepository,
            sessionManager = sessionManager,
            connectivityObserver = connectivityObserver,
            favouritesStore = favouritesStore,
            iconCacheManager = iconCacheManager,
            debugConfigManager = debugConfigManager
        )
    }

    private fun successResult(
        objects: List<SolverObject>,
        isFromCache: Boolean = false,
        lastSyncedAt: Long? = 1234L
    ) = ObjectsLoadResult.Success(
        objects = objects,
        isFromCache = isFromCache,
        lastSyncedAt = lastSyncedAt
    )

    private fun errorResult(message: String) = ObjectsLoadResult.Error(
        exception = ApiException.Network(message),
        cachedObjects = null,
        lastSyncedAt = null
    )

    @Test
    fun `loads objects successfully and shows Success state`() = runTest {
        val objects = listOf(
            createTestObject(1, "Object A"),
            createTestObject(2, "Object B")
        )
        coEvery { offlineFirstRepository.loadObjects(false) } returns successResult(objects)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state but got $state", state is ObjectsUiState.Success)
        assertEquals(2, (state as ObjectsUiState.Success).objects.size)
    }

    @Test
    fun `shows Empty state when no objects returned`() = runTest {
        coEvery { offlineFirstRepository.loadObjects(false) } returns successResult(emptyList())

        val viewModel = createViewModel()

        assertEquals(ObjectsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `shows Error state on API failure`() = runTest {
        coEvery { offlineFirstRepository.loadObjects(false) } returns errorResult("Network error")

        val viewModel = createViewModel()

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
        coEvery { offlineFirstRepository.loadObjects(false) } returns successResult(objects)

        val viewModel = createViewModel()

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
        coEvery { offlineFirstRepository.loadObjects(false) } returnsMany listOf(
            errorResult("Error"),
            successResult(listOf(createTestObject(1, "Object")))
        )

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is ObjectsUiState.Error)

        viewModel.retry()

        assertTrue(viewModel.uiState.value is ObjectsUiState.Success)
    }

    @Test
    fun `refresh updates objects`() = runTest {
        coEvery { offlineFirstRepository.loadObjects(false) } returns successResult(
            listOf(createTestObject(1, "Initial"))
        )
        coEvery { offlineFirstRepository.refreshObjects() } returns successResult(
            listOf(
                createTestObject(1, "Updated"),
                createTestObject(2, "New Object")
            )
        )

        val viewModel = createViewModel()

        val initialState = viewModel.uiState.value as ObjectsUiState.Success
        assertEquals(1, initialState.objects.size)

        viewModel.refreshObjects()

        val updatedState = viewModel.uiState.value as ObjectsUiState.Success
        assertEquals(2, updatedState.objects.size)
    }

    @Test
    fun `setSearchQuery updates query state`() = runTest {
        coEvery { offlineFirstRepository.loadObjects(false) } returns successResult(emptyList())

        val viewModel = createViewModel()

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
