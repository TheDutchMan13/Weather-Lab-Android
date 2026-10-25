package com.weatherlab.android

/** One weather term with its plain explanation. Same wording as the Weather Lab glossary on the PC. */
data class Term(val key: String, val name: String, val short: String, val long: String)

val TERMS = listOf(
    Term("dew_point", "Dew point",
        "The temperature the air must cool to before dew forms. The higher it is, the muggier the air feels.",
        "Dew point is the temperature at which the air is full of water and dew starts to form. It is the best guide to how muggy the air feels. Under 55°F (13°C) is dry, 60s is comfortable to a little humid, 65–70°F is muggy, and above 70°F (21°C) is oppressive."),
    Term("feels_like", "Feels like",
        "How hot or cold the air feels to a person, combining temperature, humidity and wind.",
        "Feels like is the temperature your body experiences. In heat it uses the heat index, which adds humidity. In cold with wind it uses the wind chill. On a calm, mild day it is close to the air temperature. The station reports it, so it updates with every reading."),
    Term("heat_index", "Heat index",
        "How hot it feels in the shade once humidity is added to the air temperature.",
        "The heat index combines air temperature and humidity. It matters most when it is hot, roughly above 80°F (27°C), because sweat evaporates more slowly in humid air. Above about 103°F (39°C) on the heat index, outdoor activity carries a real risk of heat illness."),
    Term("wind_chill", "Wind chill",
        "How much colder the wind makes the air feel on your skin.",
        "Wind chill combines air temperature and wind speed. It only applies in cold weather, roughly below 50°F (10°C) with some wind. It describes how fast skin loses heat, not the air temperature itself."),
    Term("humidity", "Humidity",
        "How much water the air holds, as a share of the most it could hold at that temperature.",
        "Relative humidity is a percentage. Warm air can hold more water, so the same amount of water gives a lower percentage when it is hot. 100% means the air is saturated and dew or fog is likely."),
    Term("gust", "Gust",
        "The strongest short burst of wind, which is higher than the average wind speed.",
        "A gust is a brief peak in wind speed, usually a few seconds long. The wind speed is the average over a short time. Gusts matter for loose objects, trees and driving."),
    Term("pressure", "Pressure",
        "The weight of the air above you. Falling pressure often means worse weather, rising means clearing.",
        "Barometric pressure is shown in inches of mercury (inHg) or hectopascals (hPa). The trend compares the last three hours. A fall of 0.06 inHg or more in three hours is shown as falling fast, and storms or strong wind are possible. A rise of 0.02 inHg or more is shown as rising, and skies are likely clearing."),
    Term("rain_rate", "Rain rate",
        "How fast rain is falling right now.",
        "The rain rate is measured in inches (or millimetres) per hour. It shows how hard it is raining at this moment, not how much has fallen."),
    Term("daily_rain", "Rain today",
        "The total rain since midnight.",
        "Rain today adds up everything the gauge has caught since midnight in your station's time zone."),
    Term("uv_index", "UV index",
        "How strong the sunburn-causing sunlight is. 0–2 low, 3–5 moderate, 6–7 high, 8–10 very high, 11+ extreme.",
        "The UV index is a scale of how strong the sun's skin-damaging ultraviolet light is. Higher numbers burn faster. Use shade, a hat and sunscreen from 3 upward, and be most careful around midday."),
    Term("aqi", "Air quality index",
        "A 0–500 scale of how clean the air is. Lower is better; above 100 can affect sensitive people.",
        "The Air Quality Index (AQI) is the national scale used in the US. 0–50 is good, 51–100 moderate, 101–150 unhealthy for sensitive groups, 151–200 unhealthy, 201–300 very unhealthy, and 301 and above hazardous. It comes from AirNow, so it describes the air near your area, not at your station."),
    Term("usual", "Usual for this hour",
        "The average for this same hour on earlier days. It appears after five days of readings.",
        "Usual for this hour averages this clock hour on earlier days, so you can see whether right now is above or below normal for the time of day. Until five days have been recorded, the note shows how many are done."),
    Term("lightning", "Lightning distance",
        "How far away the storm is, estimated from the gap between the flash and the thunder.",
        "The lightning sensor estimates distance. It detects most nearby strikes but not every one, and the estimate is less precise far away. The distance that counts as close for alerts is set in Settings."),
    Term("frost", "Frost",
        "A night where the temperature is at or below freezing, 32°F (0°C).",
        "A frost forms when the temperature drops to 32°F (0°C) or below, which can damage plants. The freeze warning uses the forecast low for the night, and you can change the threshold in Settings."),
    Term("stuck", "Stuck reading",
        "A reading that has not changed for a long time, which can mean a sensor has frozen.",
        "A temperature or humidity value that stays exactly the same for half an hour or longer usually means the sensor or its connection has stopped updating. Check the sensor and its battery."),
)

/** The explanation for a tile label such as "Dew point" or "UV index", or null when the label is not a term. */
fun termForLabel(label: String): Term? {
    val l = label.trim().lowercase()
    return TERMS.firstOrNull { it.name.lowercase() == l }
}
