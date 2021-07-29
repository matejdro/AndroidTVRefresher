package com.matejdro.androidtvrefresher

import retrofit2.http.*

interface HomeAssistantApi {
    @POST("services/{domain}/{service}")
    suspend fun triggerService(
        @Path("domain") domain: String,
        @Path("service") service: String,
        @Body parameters: Map<String, String>,
        @Header("Authorization") authorization: String
    )
}