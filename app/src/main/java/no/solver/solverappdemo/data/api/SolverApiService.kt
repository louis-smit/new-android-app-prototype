package no.solver.solverappdemo.data.api

import no.solver.solverappdemo.data.models.CommandExecutionRequest
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.SolverObjectDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface SolverApiService {

    @GET("api/Object/UserObjects")
    suspend fun getUserObjects(): Response<List<SolverObjectDTO>>

    @GET("api/Object/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ): Response<SolverObjectDTO>

    @PUT("api/Object/{objectId}/Execute/{command}")
    suspend fun executeCommand(
        @Path("objectId") objectId: Int,
        @Path("command") command: String
    ): Response<ExecuteResponse>

    @PUT("api/Object/{objectId}/Execute/{command}/{input}")
    suspend fun executeCommandWithInput(
        @Path("objectId") objectId: Int,
        @Path("command") command: String,
        @Path("input") input: String
    ): Response<ExecuteResponse>

    // TODO: Add location variants when implementing geofence features
    // @PUT("api/Object/{objectId}/Execute/{command}")
    // suspend fun executeCommandWithLocation(..., @Body location: LocationData)
    // @PUT("api/Object/{objectId}/Execute/{command}/{input}")
    // suspend fun executeCommandWithInputAndLocation(..., @Body location: LocationData)
}
