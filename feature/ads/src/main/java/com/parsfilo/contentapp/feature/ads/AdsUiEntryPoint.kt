package com.parsfilo.contentapp.feature.ads

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdsUiEntryPoint {
    fun adRevenueLogger(): AdRevenueLogger

    fun adGateChecker(): AdGateChecker

    fun adsPolicyProvider(): AdsPolicyProvider
}
