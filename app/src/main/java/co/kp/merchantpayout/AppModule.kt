package co.kp.merchantpayout

import co.kp.merchantpayout.data.ActivityRepository
import co.kp.merchantpayout.data.ActivityRepositoryImpl
import co.kp.merchantpayout.data.DeviceIdProvider
import co.kp.merchantpayout.data.MerchantApi
import co.kp.merchantpayout.data.MerchantRepository
import co.kp.merchantpayout.data.MerchantRepositoryImpl
import co.kp.merchantpayout.data.PayoutRepository
import co.kp.merchantpayout.data.PayoutRepositoryImpl
import co.kp.merchantpayout.data.ServerDeviceIdProvider
import co.kp.merchantpayout.mock.MockServerManager
import co.kp.merchantpayout.security.AndroidBiometricGate
import co.kp.merchantpayout.security.BiometricGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        @OptIn(ExperimentalSerializationApi::class)
        namingStrategy = JsonNamingStrategy.SnakeCase
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        // TODO: turn off BODY log in release build. leak sensitive stuff.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val baseUrl = MockServerManager.baseUrl
        check(baseUrl.isNotBlank()) {
            "MockServer.baseUrl is empty. did MerchantApp.onCreate() start the server?"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideMerchantApi(retrofit: Retrofit): MerchantApi =
        retrofit.create(MerchantApi::class.java)

    // ─── Repositories ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMerchantRepository(impl: MerchantRepositoryImpl): MerchantRepository = impl

    @Provides
    @Singleton
    fun provideActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository = impl

    @Provides
    @Singleton
    fun providePayoutRepository(impl: PayoutRepositoryImpl): PayoutRepository = impl

    // ─── Clock + Zone ──────────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideZone(): ZoneId = ZoneId.systemDefault()


    @Provides
    @Singleton
    fun provideDeviceIdProvider(impl: ServerDeviceIdProvider): DeviceIdProvider = impl


    @Provides
    @Singleton
    fun provideBiometricGate(impl: AndroidBiometricGate): BiometricGate = impl
}