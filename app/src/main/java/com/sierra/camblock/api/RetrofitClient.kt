package com.sierra.camblock.api

import com.sierra.camblock.BuildConfig
import com.sierra.camblock.utils.Constants
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    
    private var retrofit: Retrofit? = null
    
    private fun getRetrofitInstance(): Retrofit {
        if (retrofit == null) {

            // Logging interceptor — BODY in debug, NONE in release so we
            // don't buffer full response bodies in memory (the BODY logger
            // prints via android.util.Log.i which is stripped by
            // `-assumenosideeffects` in release anyway, so it's pure waste).
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            // OkHttp client configuration
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Constants.NETWORK_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
            
            // Gson configuration
            val gson = GsonBuilder()
                .setLenient()
                .create()
            
            // Retrofit instance
            retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        
        return retrofit!!
    }
    
    /**
     * Get API service instance
     */
    val apiService: ApiService by lazy {
        getRetrofitInstance().create(ApiService::class.java)
    }
}
