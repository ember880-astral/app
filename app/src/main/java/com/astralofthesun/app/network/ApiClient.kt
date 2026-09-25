package com.astralofthesun.app.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/* ============================================================
   Astral of the Sun — API client
   Base URL for the live backend (astral-bot on Railway).

   Auth is token-based: /api/auth/verify-otp signs a JWT login
   token, and every other endpoint expects it as
   "Authorization: Bearer <token>". The token is persisted in
   SharedPreferences and attached automatically by an OkHttp
   interceptor; a 401 anywhere means the token expired, so the
   app drops it and returns to the login screen.
   ============================================================ */

object ApiConfig {
    const val BASE_URL = "https://astral-bot-production-afb0.up.railway.app/"
}

/** Persists the JWT login token across app restarts. */
object AuthTokenStore {
    private const val PREFS = "astral_auth"
    private const val KEY_TOKEN = "token"
    private lateinit var prefs: SharedPreferences

    var token: String? = null
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        token = prefs.getString(KEY_TOKEN, null)
    }

    fun save(newToken: String) {
        token = newToken
        if (::prefs.isInitialized) prefs.edit().putString(KEY_TOKEN, newToken).apply()
    }

    fun clear() {
        token = null
        if (::prefs.isInitialized) prefs.edit().remove(KEY_TOKEN).apply()
    }
}

object ApiClient {
    private var retrofit: Retrofit? = null

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Call once, e.g. from Application/MainActivity, before using [api]. */
    fun init(context: Context) {
        if (retrofit != null) return
        AuthTokenStore.init(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                // Attach the login token to every request once we have one.
                val original = chain.request()
                val token = AuthTokenStore.token
                val request = if (token.isNullOrBlank() || original.header("Authorization") != null) original
                else original.newBuilder().header("Authorization", "Bearer $token").build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 && AuthTokenStore.token != null) {
                    // Token expired or revoked — drop it so the app re-authenticates.
                    AuthTokenStore.clear()
                    AuthState.isLoggedIn.value = false
                    AuthState.needsRegistration.value = false
                }
                response
            }
            .addInterceptor(logging)
            .build()

        val contentType = "application/json".toMediaType()

        retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    val api: AstralApi by lazy {
        checkNotNull(retrofit) { "ApiClient.init(context) must be called before use." }
            .create(AstralApi::class.java)
    }

    /** Public asset URL for an item image served from /assets/items/:file. */
    fun assetUrl(file: String): String = ApiConfig.BASE_URL + "assets/items/" + file
}
