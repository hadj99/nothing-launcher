package com.example.customwidgets

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

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

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

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
        @Query("count") count: Int = 1
    ): GeocodingResponse
}

@Immutable
data class WeatherState(
    val isLoading: Boolean = false,
    val cityLabel: String = "San Francisco",
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

    @SuppressLint("MissingPermission")
    suspend fun refreshWeather(fallbackCity: String) = withContext(Dispatchers.IO) {
        _weatherState.value = _weatherState.value.copy(isLoading = true, errorMessage = null)
        runCatching {
            var lat: Double? = null
            var lon: Double? = null
            var city = fallbackCity.ifBlank { "San Francisco" }

            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCoarse) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (loc != null) {
                    lat = loc.latitude
                    lon = loc.longitude
                    city = "Local GPS"
                }
            }

            if (lat == null || lon == null) {
                val geo = geoApi.searchCity(city).results?.firstOrNull()
                if (geo != null) {
                    lat = geo.latitude
                    lon = geo.longitude
                    city = geo.name
                } else {
                    lat = 37.7749
                    lon = -122.4194
                }
            }

            val forecast = weatherApi.getForecast(lat, lon)
            val current = forecast.current
            val hourly = forecast.hourly
            WeatherState(
                isLoading = false,
                cityLabel = city,
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
}
