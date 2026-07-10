package dev.sadr.atlas.di

import dev.sadr.atlas.core.CoreServiceManager
import dev.sadr.atlas.handler.*
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
    fun provideVpnNotificationManager(): VpnNotificationManager = VpnNotificationManager

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
