package no.solver.solverapp.data.api

import no.solver.solverapp.data.models.CommandExecutionRequest
import no.solver.solverapp.data.models.ExecuteResponse
import no.solver.solverapp.data.models.SolverObjectDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SolverApiService {

    @GET("api/Object/UserObjects")
    suspend fun getUserObjects(): Response<List<SolverObjectDTO>>

    @GET("api/Object/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ): Response<SolverObjectDTO>

    @POST("api/Object/{objectId}/Command")
    suspend fun executeCommand(
        @Path("objectId") objectId: Int,
        @Body request: CommandExecutionRequest
    ): Response<ExecuteResponse>
}
