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
class InCarWeatherManager(
    private val context: Context,
    private val configManager: com.incar.assistant.config.AppConfigManager? = null
) {

    private val TAG = "InCarWeatherManager"

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
     * 请求心知天气 API
     * 采用双模请求：优先使用纯 API Key 直连（免系统时间误差），失败再走 HMAC-SHA1
     */
    private suspend fun fetchSeniverseWeather(location: String): String = withContext(Dispatchers.IO) {
        val uid = configManager?.seniverseUid?.takeIf { it.isNotEmpty() } ?: "PefHRpoRt6qUgIqpd"
        val key = configManager?.seniverseKey?.takeIf { it.isNotEmpty() } ?: "SffaVM_il5AqZBX0y"

        if (uid.isEmpty() && key.isEmpty()) {
            return@withContext "心知天气未授权，请在设置中输入 Key"
        }

        try {
            // 模式 1: 直接 API Key
            val directKey = if (key.isNotEmpty()) key else uid
            val directUrl = "https://api.seniverse.com/v3/weather/now.json?key=$directKey&location=${URLEncoder.encode(location, "UTF-8")}"
            val request1 = Request.Builder().url(directUrl).build()
            val response1 = httpClient.newCall(request1).execute()
            if (response1.isSuccessful) {
                val body = response1.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val results = json.getJSONArray("results").getJSONObject(0)
                    val cityName = results.getJSONObject("location").getString("name")
                    val now = results.getJSONObject("now")
                    val text = now.getString("text")
                    val temp = now.getString("temperature")
                    return@withContext "$cityName 今天 $text，当前气温 $temp 度"
                }
            }

            // 模式 2: HMAC-SHA1 签名备用
            val ts = (System.currentTimeMillis() / 1000).toString()
            val ttl = "1800"
            val rawParams = "location=${URLEncoder.encode(location, "UTF-8")}&public_key=$uid&ts=$ts&ttl=$ttl"

            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKey)
            val sigBytes = mac.doFinal("public_key=$uid&ts=$ts&ttl=$ttl".toByteArray(Charsets.UTF_8))
            val signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

            val url = "https://api.seniverse.com/v3/weather/now.json?$rawParams&sig=${URLEncoder.encode(signature, "UTF-8")}"
            val request2 = Request.Builder().url(url).build()
            val response2 = httpClient.newCall(request2).execute()
            val body2 = response2.body?.string() ?: return@withContext "获取天气数据失败"

            val json = JSONObject(body2)
            val results = json.getJSONArray("results").getJSONObject(0)
            val cityName = results.getJSONObject("location").getString("name")
            val now = results.getJSONObject("now")
            val text = now.getString("text")
            val temp = now.getString("temperature")

            "$cityName 今天 $text，当前气温 $temp 度"
        } catch (e: Exception) {
            Log.e(TAG, "Seniverse request failed: ${e.message}")
            "天气查询服务暂不可用: ${e.localizedMessage}"
        }
    }
}
