package com.fieldproof.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface FieldProofApi {
    @GET("/health")
    suspend fun health(): HealthResponse

    @POST("/v1/observations")
    suspend fun pushObservation(@Body body: ObservationPushRequest): Response<ObservationResponse>

    @GET("/v1/observations")
    suspend fun pullObservations(@Query("updated_since_epoch_ms") updatedSinceEpochMs: Long): PullResponse

    @Multipart
    @POST("/v1/observations/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") observationId: String,
        @Part("sha256_hex") sha256Hex: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<EvidenceResponse>
}
