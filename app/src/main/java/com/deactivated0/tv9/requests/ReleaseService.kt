package com.deactivated0.tv9.requests

import retrofit2.Call
import retrofit2.http.GET

interface ReleaseService {
    @GET("kk/version.json")
    fun getRelease(
    ): Call<ReleaseResponse>
}
