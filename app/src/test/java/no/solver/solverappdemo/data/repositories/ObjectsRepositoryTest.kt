package no.solver.solverappdemo.data.repositories

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import no.solver.solverappdemo.core.config.AuthEnvironment
import no.solver.solverappdemo.core.config.AuthProvider
import no.solver.solverappdemo.core.network.ApiException
import no.solver.solverappdemo.core.network.ApiResult
import no.solver.solverappdemo.data.api.ApiClientManager
import no.solver.solverappdemo.data.api.SolverApiService
import no.solver.solverappdemo.data.models.SolverObjectDTO
import no.solver.solverappdemo.features.auth.models.AuthTokens
import no.solver.solverappdemo.features.auth.models.Session
import no.solver.solverappdemo.features.auth.services.SessionManager
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ObjectsRepositoryTest {

    private lateinit var apiClientManager: ApiClientManager
    private lateinit var sessionManager: SessionManager
    private lateinit var apiService: SolverApiService
    private lateinit var repository: ObjectsRepository

    @Before
    fun setup() {
        apiClientManager = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        
        coEvery { apiClientManager.getApiService(any(), any()) } returns apiService
    }

    private fun createRepository(): ObjectsRepository {
        return ObjectsRepository(apiClientManager, sessionManager)
    }

    private fun createSession() = Session(
        id = "test-session",
        provider = AuthProvider.MICROSOFT,
        environment = AuthEnvironment.SOLVER,
        tokens = AuthTokens(
            accessToken = "test-token",
            refreshToken = null,
            expiresAtMillis = System.currentTimeMillis() + 3600000
        ),
        isActive = true
    )

    @Test
    fun `getUserObjects returns success with mapped objects`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        
        val dtoList = listOf(
            SolverObjectDTO(
                objectId = 1,
                name = "Conference Room",
                objectTypeId = 1,
                status = "Available",
                online = true,
                active = true
            ),
            SolverObjectDTO(
                objectId = 2,
                name = "Storage",
                objectTypeId = 2,
                status = "Locked",
                online = true,
                active = true
            )
        )
        coEvery { apiService.getUserObjects() } returns Response.success(dtoList)

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Success)
        val objects = (result as ApiResult.Success).data
        assertEquals(2, objects.size)
        assertEquals("Conference Room", objects[0].name)
        assertEquals("Storage", objects[1].name)
    }

    @Test
    fun `getUserObjects returns error when no session`() = runTest {
        coEvery { sessionManager.getCurrentSession() } returns null

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.Unauthorized)
    }

    @Test
    fun `getUserObjects returns error on 401 response`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        coEvery { apiService.getUserObjects() } returns Response.error(
            401,
            "Unauthorized".toResponseBody()
        )

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.Unauthorized)
    }

    @Test
    fun `getUserObjects returns error on 404 response`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        coEvery { apiService.getUserObjects() } returns Response.error(
            404,
            "Not Found".toResponseBody()
        )

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.NotFound)
    }

    @Test
    fun `getUserObjects returns error on 500 response`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        coEvery { apiService.getUserObjects() } returns Response.error(
            500,
            "Server Error".toResponseBody()
        )

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Error)
        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.Server)
    }

    @Test
    fun `getUserObjects returns empty list when body is null`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        coEvery { apiService.getUserObjects() } returns Response.success(null)

        repository = createRepository()
        val result = repository.getUserObjects()

        assertTrue(result is ApiResult.Success)
        val objects = (result as ApiResult.Success).data
        assertTrue(objects.isEmpty())
    }

    @Test
    fun `getUserObjects uses correct API based on provider`() = runTest {
        val msSession = Session(
            id = "ms-session",
            provider = AuthProvider.MICROSOFT,
            environment = AuthEnvironment.SOLVER,
            tokens = AuthTokens("token", null, System.currentTimeMillis() + 3600000),
            isActive = true
        )
        coEvery { sessionManager.getCurrentSession() } returns msSession
        coEvery { apiService.getUserObjects() } returns Response.success(emptyList())

        repository = createRepository()
        repository.getUserObjects()

        io.mockk.coVerify {
            apiClientManager.getApiService(AuthEnvironment.SOLVER, AuthProvider.MICROSOFT)
        }
    }

    @Test
    fun `getObject returns success with mapped object`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        
        val dto = SolverObjectDTO(
            objectId = 1,
            name = "Test Object",
            objectTypeId = 1,
            status = "Available"
        )
        coEvery { apiService.getObject(1) } returns Response.success(dto)

        repository = createRepository()
        val result = repository.getObject(1)

        assertTrue(result is ApiResult.Success)
        assertEquals("Test Object", (result as ApiResult.Success).data.name)
    }

    @Test
    fun `getObject returns NotFound when object is null`() = runTest {
        val session = createSession()
        coEvery { sessionManager.getCurrentSession() } returns session
        coEvery { apiService.getObject(999) } returns Response.success(null)

        repository = createRepository()
        val result = repository.getObject(999)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).exception is ApiException.NotFound)
    }
}
