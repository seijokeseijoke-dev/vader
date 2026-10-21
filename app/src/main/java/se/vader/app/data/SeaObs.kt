package se.vader.app.data

/**
 * SMHI oceanografiska observationer, parameter 5 = Havstemperatur (°C).
 * Stationslista (med mobile-flagga för fartyg/isbrytare):
 *   https://opendata-download-ocobs.smhi.se/api/version/latest/parameter/5.json
 * Senaste dygnets värden för alla stationer (med koordinater):
 *   .../parameter/5/station-set/all/period/latest-day/data.json
 */
data class OcobsMeta(val station: List<OcobsMetaStation>?)

data class OcobsMetaStation(val key: String?, val name: String?, val mobile: Boolean?)

data class OcobsLatest(val station: List<OcobsStation>?)

data class OcobsStation(
    val key: String?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val value: List<OcobsValue>?
)

data class OcobsValue(
    val date: Long?,
    val value: Double?,
    val quality: String?,
    val depth: String?
)
