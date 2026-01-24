package no.solver.solverappdemo.data.api

import no.solver.solverappdemo.data.models.CommandExecutionRequest
import no.solver.solverappdemo.data.models.ExecuteResponse
import no.solver.solverappdemo.data.models.InitiatePaymentRequest
import no.solver.solverappdemo.data.models.InitiateSubscriptionRequest
import no.solver.solverappdemo.data.models.ObjectLog
import no.solver.solverappdemo.data.models.PaymentResponse
import no.solver.solverappdemo.data.models.ResourceIcon
import no.solver.solverappdemo.data.models.SolverObjectDTO
import no.solver.solverappdemo.data.models.StripeOrder
import no.solver.solverappdemo.data.models.SubscriptionOption
import no.solver.solverappdemo.data.models.SubscriptionPaymentResponse
import no.solver.solverappdemo.data.models.VippsOrder
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SolverApiService {

    // Resource Icons - fetch all icons in one request for pre-caching
    @GET("api/Resource/Icons")
    suspend fun getResourceIcons(): Response<List<ResourceIcon>>

    @GET("api/Object/UserObjects")
    suspend fun getUserObjects(): Response<List<SolverObjectDTO>>

    @GET("api/Object/{objectId}")
    suspend fun getObject(
        @Path("objectId") objectId: Int
    ): Response<SolverObjectDTO>

    // Favourites
    @GET("api/User/Favourite")
    suspend fun getFavourites(): Response<List<SolverObjectDTO>>

    @GET("api/User/Favourite/Object/{objectId}/IsFavourite")
    suspend fun isFavourite(
        @Path("objectId") objectId: Int
    ): Response<Boolean>

    @retrofit2.http.POST("api/User/Favourite/Object/{objectId}")
    suspend fun addFavourite(
        @Path("objectId") objectId: Int
    ): Response<Unit>

    @retrofit2.http.DELETE("api/User/Favourite/Object/{objectId}")
    suspend fun removeFavourite(
        @Path("objectId") objectId: Int
    ): Response<Unit>

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

    // Search
    @GET("api/Object/UserObjects/v2/Search")
    suspend fun searchObjects(
        @retrofit2.http.Query("query") query: String
    ): Response<List<SolverObjectDTO>>

    // Logs
    @GET("api/Object/UserLogs")
    suspend fun getUserLogs(): Response<List<ObjectLog>>

    @GET("api/Object/{objectId}/UserLogs")
    suspend fun getObjectLogs(
        @Path("objectId") objectId: Int
    ): Response<List<ObjectLog>>

    @GET("api/Object/DelegatedObjects/Logs")
    suspend fun getAdminLogs(): Response<List<ObjectLog>>

    // Payments - get signed URL for payment portal
    @POST("api/System/SignedURL")
    suspend fun getSignedUrl(): Response<ResponseBody>

    // TODO: Add location variants when implementing geofence features
    // @PUT("api/Object/{objectId}/Execute/{command}")
    // suspend fun executeCommandWithLocation(..., @Body location: LocationData)
    // @PUT("api/Object/{objectId}/Execute/{command}/{input}")
    // suspend fun executeCommandWithInputAndLocation(..., @Body location: LocationData)

    // Tag-based operations for QR/NFC deep linking
    @GET("api/Object/Tag/{tagId}")
    suspend fun getObjectByTag(
        @Path("tagId") tagId: String
    ): Response<SolverObjectDTO>

    @PUT("api/Object/Execute/{tag}/{command}")
    suspend fun executeCommandByTag(
        @Path("tag") tag: String,
        @Path("command") command: String
    ): Response<ExecuteResponse>

    // ==================== PAYMENT ENDPOINTS ====================

    // Vipps payment initiation
    @POST("api/Object/{objectId}/v2/InitiatePayment")
    suspend fun initiateVippsPayment(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiatePaymentRequest
    ): Response<PaymentResponse>

    // Card payment initiation
    @POST("api/Object/{objectId}/v2/InitiateCardPayment")
    suspend fun initiateCardPayment(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiatePaymentRequest
    ): Response<PaymentResponse>

    // Stripe payment initiation
    @POST("api/Stripe/Object/{objectId}/v2/InitiateStripePayment")
    suspend fun initiateStripePayment(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiatePaymentRequest
    ): Response<PaymentResponse>

    // Vipps recurring payment initiation (for subscriptionTypeId == 3)
    @POST("api/Object/{objectId}/RecurringPayment")
    suspend fun initiateVippsRecurringPayment(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiateSubscriptionRequest
    ): Response<SubscriptionPaymentResponse>

    // Payment status checks
    @GET("api/Vipps/VippsPayment/{orderId}")
    suspend fun getVippsPaymentStatus(
        @Path("orderId") orderId: String
    ): Response<VippsOrder>

    @GET("api/Vipps/VippsRecurringPayment/{orderId}")
    suspend fun getVippsRecurringPaymentStatus(
        @Path("orderId") orderId: String
    ): Response<VippsOrder>

    @GET("api/Vipps/CardPayment/{reference}")
    suspend fun getCardPaymentStatus(
        @Path("reference") reference: String
    ): Response<VippsOrder>

    @GET("api/Stripe/StripePayment/{reference}")
    suspend fun getStripePaymentStatus(
        @Path("reference") reference: String
    ): Response<StripeOrder>

    // ==================== SUBSCRIPTION ENDPOINTS ====================

    // Get available subscription options for an object
    @GET("api/Object/{objectId}/SubscriptionOptions")
    suspend fun getSubscriptionOptions(
        @Path("objectId") objectId: Int
    ): Response<List<SubscriptionOption>>

    // Initiate subscription with Vipps (regular subscription, typeId == 1)
    @POST("api/Object/{objectId}/v2/InitiatePayment")
    suspend fun initiateVippsSubscription(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiateSubscriptionRequest
    ): Response<SubscriptionPaymentResponse>

    // Initiate subscription with Card
    @POST("api/Object/{objectId}/v2/InitiateCardPayment")
    suspend fun initiateCardSubscription(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiateSubscriptionRequest
    ): Response<SubscriptionPaymentResponse>

    // Initiate subscription with Stripe
    @POST("api/Stripe/Object/{objectId}/v2/InitiateStripePayment")
    suspend fun initiateStripeSubscription(
        @Path("objectId") objectId: Int,
        @Query("webCallbackURI") webCallbackUri: String,
        @Body request: InitiateSubscriptionRequest
    ): Response<SubscriptionPaymentResponse>
}
