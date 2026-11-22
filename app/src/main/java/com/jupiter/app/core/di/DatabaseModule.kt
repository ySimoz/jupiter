package com.jupiter.app.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.jupiter.app.data.local.DataStoreLibraryFolderPreferences
import com.jupiter.app.data.local.JupiterDatabase
import com.jupiter.app.data.local.LibraryFolderPreferences
import com.jupiter.app.data.local.MediaHistoryDao
import com.jupiter.app.data.local.NetworkStreamDao
import com.jupiter.app.data.media.AndroidSafFolderScanner
import com.jupiter.app.data.media.AndroidUriResolver
import com.jupiter.app.data.media.LocalMediaScanner
import com.jupiter.app.data.media.MediaStoreLocalMediaScanner
import com.jupiter.app.data.media.SafFolderScanner
import com.jupiter.app.data.media.UriResolver
import com.jupiter.app.data.repository.DataStoreSettingsRepository
import com.jupiter.app.data.repository.DefaultMediaRepository
import com.jupiter.app.data.repository.DefaultStreamRepository
import com.jupiter.app.data.repository.MediaRepository
import com.jupiter.app.data.repository.SettingsRepository
import com.jupiter.app.data.repository.StreamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.jupiterPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jupiter_preferences",
)

private val Context.libraryFoldersDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jupiter_library_folders",
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJupiterDatabase(@ApplicationContext context: Context): JupiterDatabase =
        Room.databaseBuilder(
            context,
            JupiterDatabase::class.java,
            "jupiter.db",
        ).build()

    @Provides
    fun provideMediaHistoryDao(database: JupiterDatabase): MediaHistoryDao =
        database.mediaHistoryDao()

    @Provides
    fun provideNetworkStreamDao(database: JupiterDatabase): NetworkStreamDao =
        database.networkStreamDao()

    @Provides
    @Singleton
    fun provideLocalMediaScanner(@ApplicationContext context: Context): LocalMediaScanner =
        MediaStoreLocalMediaScanner(context)

    @Provides
    @Singleton
    fun provideSafFolderScanner(@ApplicationContext context: Context): SafFolderScanner =
        AndroidSafFolderScanner(context)

    @Provides
    @Singleton
    fun provideLibraryFolderPreferences(
        @ApplicationContext context: Context,
    ): LibraryFolderPreferences = DataStoreLibraryFolderPreferences(context.libraryFoldersDataStore)

    @Provides
    @Singleton
    fun provideUriResolver(@ApplicationContext context: Context): UriResolver =
        AndroidUriResolver(context)

    @Provides
    @Singleton
    fun provideMediaRepository(
        scanner: LocalMediaScanner,
        mediaHistoryDao: MediaHistoryDao,
        folderPreferences: LibraryFolderPreferences,
        safScanner: SafFolderScanner,
    ): MediaRepository = DefaultMediaRepository(
        scanner = scanner,
        mediaHistoryDao = mediaHistoryDao,
        folderPreferences = folderPreferences,
        safScanner = safScanner,
    )

    @Provides
    @Singleton
    fun provideStreamRepository(
        networkStreamDao: NetworkStreamDao,
    ): StreamRepository = DefaultStreamRepository(
        networkStreamDao = networkStreamDao,
    )

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.jupiterPreferencesDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>,
        @MpvConfigDir configDir: String,
        @ApplicationContext context: Context,
    ): SettingsRepository = DataStoreSettingsRepository(
        dataStore = dataStore,
        configDir = configDir,
        context = context,
    )
}
