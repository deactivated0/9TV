package com.deactivated0.tv9.requests


data class TimeResponse(
    val data: Time
) {
    data class Time(
        val t: String
    )
}