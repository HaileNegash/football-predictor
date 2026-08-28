package com.example.network

import com.example.models.ApiFootballResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiFootballService {
    @GET("fixtures")
    suspend fun getFixtures(
        @Header("x-apisports-key") apiKey: String,
        @Query("date") date: String
    ): ApiFootballResponse
}
