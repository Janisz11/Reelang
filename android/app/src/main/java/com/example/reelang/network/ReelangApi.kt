package com.example.reelang.network

import com.example.reelang.network.models.CaptionSegment
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.network.models.SaveWordRequest
import com.example.reelang.network.models.WordResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ReelangApi {

    @GET("reels")
    suspend fun getReels(): List<ReelResponse>

    @GET("reels/{id}/captions")
    suspend fun getCaptions(
        @Path("id") reelId: String
    ): List<CaptionSegment>

    @POST("words")
    suspend fun saveWord(
        @Body request: SaveWordRequest
    ): WordResponse

    @GET("words")
    suspend fun getWords(): List<WordResponse>

    @GET("words/{id}")
    suspend fun getWordById(
        @Path("id") wordId: String
    ): WordResponse
}
