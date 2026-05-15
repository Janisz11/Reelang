package com.example.reelang.network

import com.example.reelang.network.models.ActivityLogRequest
import com.example.reelang.network.models.ActivityStatsResponse
import com.example.reelang.network.models.CaptionSegment
import com.example.reelang.network.models.FollowResponse
import com.example.reelang.network.models.ProfileResponse
import com.example.reelang.network.models.ProfileUpdateRequest
import com.example.reelang.network.models.ReelResponse
import com.example.reelang.network.models.SaveWordRequest
import com.example.reelang.network.models.WordResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ReelangApi {

    @Multipart
    @POST("reels/upload")
    suspend fun uploadReel(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("language") language: RequestBody,
        @Part("tags") tags: RequestBody,
        @Part("owner_user_id") ownerUserId: RequestBody
    ): retrofit2.Response<Unit>

    @GET("reels")
    suspend fun getReels(
        @Query("language") language: String? = null,
        @Query("level") level: String? = null,
        @Query("topic") topic: String? = null,
        @Query("tags") tags: String? = null
    ): List<ReelResponse>

    @GET("reels/{id}/captions")
    suspend fun getCaptions(
        @Path("id") reelId: String
    ): List<CaptionSegment>

    @GET("reels/user/{userId}")
    suspend fun getUserReels(@Path("userId") userId: String): List<ReelResponse>

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

    // ── Profiles ─────────────────────────────────────────────────────────────

    @GET("profiles/me")
    suspend fun getMyProfile(@Query("user_id") userId: String): ProfileResponse

    @PUT("profiles/me")
    suspend fun updateMyProfile(
        @Query("user_id") userId: String,
        @Body body: ProfileUpdateRequest
    ): ProfileResponse

    @GET("profiles/me/stats")
    suspend fun getMyStats(@Query("user_id") userId: String): ActivityStatsResponse

    @GET("profiles/search")
    suspend fun searchProfiles(
        @Query("q") query: String,
        @Query("user_id") userId: String? = null
    ): List<ProfileResponse>

    @GET("profiles/{profileUserId}")
    suspend fun getProfile(
        @Path("profileUserId") profileUserId: String,
        @Query("user_id") userId: String? = null
    ): ProfileResponse

    @POST("profiles/{profileUserId}/follow")
    suspend fun followUser(
        @Path("profileUserId") profileUserId: String,
        @Query("user_id") userId: String
    ): FollowResponse

    // ── Activity ──────────────────────────────────────────────────────────────

    @POST("activity/log")
    suspend fun logActivity(
        @Query("user_id") userId: String,
        @Body body: ActivityLogRequest
    ): retrofit2.Response<Unit>
}
