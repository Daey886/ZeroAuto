package com.incar.assistant.weather

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * 车载天气双轨分流模块
 * 1. 本地首选：高德广播缓存（0 API、0 流量、断网可用）
 * 2. 备选兜底：心知天气 API（长途跨城、多日预报、异地查询）
 */
class InCarWeatherManager(private val context: Context) {

    private val TAG = "InCarWeather"

    // 心知天气凭据
    private val SENIVERSE_UID = "PefHRpoRt6qUgIqpd"
    private val SENIVERSE_KEY = "SffaVM_il5AqZBX0y"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 语音获取当前天气（双轨策略）
     */
    suspend fun queryWeather(city: String = "taizhou", isRemoteOrMultiDay: Boolean = false): String {
        // 如果是长途异地或者未来多天，直接走心知天气 API
        if (isRemoteOrMultiDay) {
            return fetchSeniverseWeather(city)
        }

        // 本地常规查询：优先查高德广播气象缓存
        val localAmapWeather = getAmapCachedWeather()
        if (localAmapWeather != null) {
            return localAmapWeather
        }

        // 缓存未就绪，降级至心知天气
        return fetchSeniverseWeather(city)
    }

    private fun getAmapCachedWeather(): String? {
        // 读取高德车机版广播同步的本地天气，断网 0ms 返回
        // 返回示例: "当前台州晴，气温24度"
        return null // 模拟冷启动兜底
    }

    /**
     * 请求心知天气 API（HMAC-SHA1 签名认证）
     */
    private suspend fun fetchSeniverseWeather(location: String): String = withContext(Dispatchers.IO) {
        try {
            val ts = (System.currentTimeMillis() / 1000).toString()
            val ttl = "1800"
            val rawParams = "location=${URLEncoder.encode(location, "UTF-8")}&ts=$ts&ttl=$ttl&uid=$SENIVERSE_UID"

            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(SENIVERSE_KEY.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKey)
            val sigBytes = mac.doFinal("ts=$ts&ttl=$ttl&uid=$SENIVERSE_UID".toByteArray(Charsets.UTF_8))
            val signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

            val url = "https://api.seniverse.com/v3/weather/now.json?$rawParams&sig=${URLEncoder.encode(signature, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "HermesInCar/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "获取天气数据失败"

            val json = JSONObject(body)
            val results = json.getJSONArray("results").getJSONObject(0)
            val cityName = results.getJSONObject("location").getString("name")
            val now = results.getJSONObject("now")
            val text = now.getString("text")
            val temp = now.getString("temperature")

            "$cityName 今天 $text，当前气温 $temp 度"
        } catch (e: Exception) {
            Log.e(TAG, "Seniverse request failed: ${e.message}")
            "天气查询服务暂不可用"
        }
    }
}
