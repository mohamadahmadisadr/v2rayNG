package com.v2ray.ang.di

import android.app.Application
import android.content.Context
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.handler.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(application: Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideSettingsManager(): SettingsManager = SettingsManager

    @Provides
    @Singleton
    fun provideMmkvManager(): MmkvManager = MmkvManager

    @Provides
    @Singleton
    fun provideCoreServiceManager(): CoreServiceManager = CoreServiceManager

    @Provides
    @Singleton
    fun provideAngConfigManager(): AngConfigManager = AngConfigManager

    @Provides
    @Singleton
    fun provideNotificationManager(): NotificationManager = NotificationManager

    @Provides
    @Singleton
    fun provideAutoBestConfigManager(): AutoBestConfigManager = AutoBestConfigManager

    @Provides
    @Singleton
    fun provideSpeedtestManager(): SpeedtestManager = SpeedtestManager

    @Provides
    @Singleton
    fun provideSubscriptionUpdater(): SubscriptionUpdater = SubscriptionUpdater

    @Provides
    @Singleton
    fun provideWebDavManager(): WebDavManager = WebDavManager

    @Provides
    @Singleton
    fun provideUpdateCheckerManager(): UpdateCheckerManager = UpdateCheckerManager

    @Provides
    @Singleton
    fun provideSettingsChangeManager(): SettingsChangeManager = SettingsChangeManager
}
