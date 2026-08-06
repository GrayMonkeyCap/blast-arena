package com.lifeledger.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.lifeledger.core.common.di.ApplicationScope
import com.lifeledger.core.datastore.DataStorePreferencesRepository
import com.lifeledger.core.datastore.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindsPreferencesRepository(
        impl: DataStorePreferencesRepository,
    ): PreferencesRepository

    companion object {
        private const val PREFERENCES_FILE_NAME = "life_ledger_prefs"

        @Provides
        @Singleton
        fun providesPreferencesDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope scope: CoroutineScope,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
    }
}
