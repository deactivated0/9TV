package com.deactivated0.tv9.requests

import retrofit2.Call
import retrofit2.http.Url

interface ConfigService {
    fun getConfig(
        @Url url: String
    ): Call<String>
}