package com.sierra.camblock.api

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
            
            // Logging interceptor for debugging
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
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
