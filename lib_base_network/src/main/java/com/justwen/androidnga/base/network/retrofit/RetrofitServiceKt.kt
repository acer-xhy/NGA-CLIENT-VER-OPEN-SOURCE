package com.justwen.androidnga.base.network.retrofit

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Retrofit 声明层统一返回 [Call]，不再用 Kotlin suspend 声明。
 *
 * 原因（release + R8 实测）：suspend 方法编译器会生成 `Continuation<? super String>` 参数，
 * R8（AGP 9 内置版本）重写方法泛型签名时把该参数的实参丢掉，dex 里只剩裸 `Continuation`，
 * Retrofit 解析方法返回值时 `(ParameterizedType)` 强转直接抛
 * `java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType`，
 * 请求整条链路静默失败（板块在线更新、消息、个人资料、过滤词都受影响）。
 * 改成 `Call<String>` 后 Retrofit 只解析 Call 的实参（String），不再触碰 Continuation。
 *
 * 协程侧通过 [awaitString] 扩展桥接，语义与原 Retrofit suspend 实现一致。
 */
interface RetrofitServiceKt {

    @GET("nuke.php")
    fun getString(@QueryMap map: Map<String, String>): Call<String>

    @GET("nuke.php")
    fun getStringEncoded(@QueryMap(encoded = true) map: Map<String, String>): Call<String>

    @GET
    fun getString(@Url url: String): Call<String>

    @FormUrlEncoded
    @POST("nuke.php")
    @Headers(
        "Content-Type: application/x-www-form-urlencoded",
        "Accept-Charset: GBK",
    )
    fun post(
        @QueryMap queryMap: Map<String, String> = HashMap(),
        @FieldMap fieldMap: Map<String, String> = HashMap()
    ): Call<String>

    @FormUrlEncoded
    @POST("nuke.php")
    @Headers(
        "Content-Type: application/x-www-form-urlencoded",
        "Accept-Charset: GBK",
    )
    fun postString(
        @QueryMap queryMap: Map<String, String> = emptyMap(),
        @HeaderMap headerMap: Map<String, String> = emptyMap(),
        @FieldMap fieldMap: Map<String, String> = emptyMap()
    ): Call<String>
}

/**
 * 把 [Call] 桥接成协程结果，语义与 Retrofit 自带的 suspend 实现保持一致：
 * 成功取 body，非 2xx 抛 [HttpException]，网络/解析异常原样抛出。
 */
suspend fun Call<String>.awaitString(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback<String> {
        override fun onResponse(call: Call<String>, response: Response<String>) {
            if (!continuation.isActive) {
                return
            }
            val body = response.body()
            if (!response.isSuccessful) {
                continuation.resumeWith(Result.failure(HttpException(response)))
            } else if (body == null) {
                continuation.resumeWith(Result.failure(IllegalStateException("响应体为空")))
            } else {
                continuation.resumeWith(Result.success(body))
            }
        }

        override fun onFailure(call: Call<String>, t: Throwable) {
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(t))
            }
        }
    })
}
