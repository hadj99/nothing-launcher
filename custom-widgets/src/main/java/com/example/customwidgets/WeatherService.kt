package com.example.customwidgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import kotlin.coroutines.resume

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null,
    val hourly: HourlyWeatherDto? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @Json(name = "temperature_2m") val temperature2m: Double = 0.0,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int = 0,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
    @Json(name = "weather_code") val weatherCode: Int = 0
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    @Json(name = "temperature_2m") val temperature2m: List<Double> = emptyList(),
    @Json(name = "uv_index") val uvIndex: List<Double> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<GeocodingResultDto>? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null
) {
    fun displayLabel(): String {
        val parts = listOfNotNull(
            name.takeIf { it.isNotBlank() },
            admin1?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) },
            country?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(", ")
    }
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
        @Query("hourly") hourly: String = "temperature_2m,uv_index",
        @Query("forecast_days") forecastDays: Int = 1
    ): OpenMeteoResponse
}

interface OpenMeteoGeoApi {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("count") count: Int = 6
    ): GeocodingResponse
}

@Immutable
data class WeatherState(
    val isLoading: Boolean = false,
    val cityLabel: String = "Oran",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isPreciseGps: Boolean = false,
    val temperatureC: Double? = null,
    val humidityPercent: Int = 0,
    val windKmh: Double = 0.0,
    val uvIndex: Double = 0.0,
    val weatherCode: Int = 0,
    val hourlyTemps: List<Double> = emptyList(),
    val errorMessage: String? = null
) {
    val conditionText: String
        get() = when (weatherCode) {
            0 -> "CLEAR SKY"
            1, 2, 3 -> "PARTLY CLOUDY"
            45, 48 -> "FOG"
            51, 53, 55, 61, 63, 65 -> "RAIN"
            71, 73, 75 -> "SNOW"
            80, 81, 82 -> "SHOWERS"
            95, 96, 99 -> "THUNDERSTORM"
            else -> "OVERCAST"
        }
}

class WeatherRepository(private val context: Context) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val weatherApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoApi::class.java)

    private val geoApi: OpenMeteoGeoApi = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoGeoApi::class.java)

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    suspend fun searchLocations(query: String): List<GeocodingResultDto> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        runCatching {
            geoApi.searchCity(query.trim(), count = 6).results.orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun refreshWeather(fallbackCity: String) {
        refreshWeather(WidgetAppearanceConfig(weatherCity = fallbackCity))
    }

    @SuppressLint("MissingPermission")
    suspend fun refreshWeather(config: WidgetAppearanceConfig) = withContext(Dispatchers.IO) {
        _weatherState.value = _weatherState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            var lat: Double? = null
            var lon: Double? = null
            var city = config.weatherCity.ifBlank { "Oran" }
            var usedGps = false

            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (config.weatherUsePreciseGps && (hasFine || hasCoarse)) {
                val loc = resolveBestDeviceLocation(hasFine)
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    usedGps = true
                    city = reverseGeocodeLabel(lat, lon)
                        ?: String.format(Locale.US, "GPS %.2f°, %.2f°", lat, lon)
                }
            }

            if ((lat == null || lon == null) && config.weatherCustomLat != null && config.weatherCustomLon != null) {
                lat = config.weatherCustomLat
                lon = config.weatherCustomLon
                city = config.weatherCity.ifBlank {
                    String.format(Locale.US, "%.3f°, %.3f°", lat, lon)
                }
            }

            if (lat == null || lon == null) {
                val geo = geoApi.searchCity(city, count = 1).results?.firstOrNull()
                if (geo != null) {
                    lat = geo.latitude
                    lon = geo.longitude
                    city = geo.name
                } else {
                    lat = 35.6971
                    lon = -0.6308
                    city = "Oran"
                }
            }

            val forecast = weatherApi.getForecast(lat, lon)
            val current = forecast.current
            val hourly = forecast.hourly
            WeatherState(
                isLoading = false,
                cityLabel = city,
                latitude = lat,
                longitude = lon,
                isPreciseGps = usedGps,
                temperatureC = current?.temperature2m,
                humidityPercent = current?.relativeHumidity2m ?: 0,
                windKmh = current?.windSpeed10m ?: 0.0,
                uvIndex = hourly?.uvIndex?.firstOrNull() ?: 0.0,
                weatherCode = current?.weatherCode ?: 0,
                hourlyTemps = hourly?.temperature2m?.take(12).orEmpty(),
                errorMessage = null
            )
        }.onSuccess {
            _weatherState.value = it
        }.onFailure { err ->
            _weatherState.value = _weatherState.value.copy(
                isLoading = false,
                errorMessage = err.localizedMessage ?: "Network unavailable"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveBestDeviceLocation(hasFine: Boolean): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (hasFine) {
                add(LocationManager.GPS_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val candidates = providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }
        val bestCached = candidates.maxByOrNull { it.time }
        val now = System.currentTimeMillis()
        if (bestCached != null && now - bestCached.time < 30 * 60 * 1000L) {
            return bestCached
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val activeProvider = when {
                hasFine && runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.GPS_PROVIDER
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (activeProvider != null) {
                val fresh = withTimeoutOrNull(4200L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val cancelSignal = CancellationSignal()
                        cont.invokeOnCancellation { cancelSignal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(
                                activeProvider,
                                cancelSignal,
                                ContextCompat.getMainExecutor(context)
                            ) { loc ->
                                if (cont.isActive) cont.resume(loc)
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return bestCached
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeLabel(lat: Double, lon: Double): String? {
        return runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addr = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return null
            val locality = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: addr.adminArea
            val region = addr.adminArea ?: addr.countryName
            when {
                !locality.isNullOrBlank() && !region.isNullOrBlank() && !locality.equals(region, ignoreCase = true) ->
                    "$locality, $region"
                !locality.isNullOrBlank() -> locality
                else -> region
            }
        }.getOrNull()
    }
}
