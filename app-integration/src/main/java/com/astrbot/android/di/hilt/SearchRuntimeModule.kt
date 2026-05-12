package com.astrbot.android.di.hilt

import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.search.SearchProvider
import com.astrbot.android.core.runtime.search.UnifiedSearchCoordinator
import com.astrbot.android.core.runtime.search.UnifiedSearchPort
import com.astrbot.android.core.runtime.search.api.BaiduAiSearchProvider
import com.astrbot.android.core.runtime.search.api.BoChaSearchProvider
import com.astrbot.android.core.runtime.search.api.BraveSearchProvider
import com.astrbot.android.core.runtime.search.api.TavilySearchProvider
import com.astrbot.android.core.runtime.search.local.LocalMetaSearchFallbackProvider
import com.astrbot.android.core.runtime.search.local.crawl.ContentCrawlerLite
import com.astrbot.android.core.runtime.search.local.crawl.DefaultContentCrawlerLite
import com.astrbot.android.core.runtime.search.local.engine.BaiduWebLiteEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.BingEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.BingNewsEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.DuckDuckGoLiteEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.SearchEngineAdapter
import com.astrbot.android.core.runtime.search.local.engine.SogouEngineAdapter
import com.astrbot.android.core.runtime.search.local.parser.BaiduWebParser
import com.astrbot.android.core.runtime.search.local.parser.BingNewsResultParser
import com.astrbot.android.core.runtime.search.local.parser.BingResultParser
import com.astrbot.android.core.runtime.search.local.parser.DuckDuckGoLiteParser
import com.astrbot.android.core.runtime.search.local.parser.SogouResultParser
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.provider.runtime.search.ProviderRepositorySearchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object SearchRuntimeModule {

    @Provides
    @Singleton
    fun provideProviderRepositorySearchProvider(
        providerRepository: ProviderRepositoryPort,
        transport: RuntimeNetworkTransport,
    ): ProviderRepositorySearchProvider {
        return ProviderRepositorySearchProvider(
            providerRepository = providerRepository,
            apiProviders = listOf(
                TavilySearchProvider(transport),
                BraveSearchProvider(transport),
                BoChaSearchProvider(transport),
                BaiduAiSearchProvider(transport),
            ),
        )
    }

    @Provides
    @Singleton
    fun provideLocalSearchEngines(
        transport: RuntimeNetworkTransport,
    ): List<SearchEngineAdapter> {
        return listOf(
            SogouEngineAdapter(transport, SogouResultParser()),
            BingEngineAdapter(transport, BingResultParser()),
            BaiduWebLiteEngineAdapter(transport, BaiduWebParser()),
            DuckDuckGoLiteEngineAdapter(transport, DuckDuckGoLiteParser()),
            BingNewsEngineAdapter(transport, BingNewsResultParser()),
        )
    }

    @Provides
    @Singleton
    fun provideContentCrawlerLite(
        crawler: DefaultContentCrawlerLite,
    ): ContentCrawlerLite = crawler

    @Provides
    @Singleton
    fun provideUnifiedSearchPort(
        providerRepositorySearchProvider: ProviderRepositorySearchProvider,
        localMetaSearchFallbackProvider: LocalMetaSearchFallbackProvider,
    ): UnifiedSearchPort {
        val providers: List<SearchProvider> = listOf(
            providerRepositorySearchProvider,
            localMetaSearchFallbackProvider,
        )
        return UnifiedSearchCoordinator(providers)
    }
}
