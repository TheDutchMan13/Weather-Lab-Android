package com.weatherlab.android

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One number from Weather Lab, already in the units chosen on the PC, with its unit label. */
data class Measure(val value: Double, val unit: String)

/** [words] is the plain-language wording Weather Lab writes for dew point, wind direction and UV (empty from versions before 0.37.36). */
data class Current(val ageSeconds: Int?, val values: Map<String, Measure>, val metric: Boolean, val words: Map<String, String> = emptyMap())
data class Today(val date: String, val high: Measure?, val low: Measure?, val rain: Measure?, val readings: Int)
data class Status(val station: String, val appVersion: String, val ageSeconds: Int?)
data class Alert(val ts: Long, val text: String)
/** The last 24 hours of temperature, oldest first: (timestamp in seconds, value). */
data class History(val unit: String, val points: List<Pair<Long, Double>>)
data class WeekDay(val date: String, val high: Double?, val low: Double?, val rain: Double?)
data class Week(val days: List<WeekDay>, val tempUnit: String, val rainUnit: String)
data class Strike(val ts: Long, val ageSeconds: Long, val distance: Measure)
/** trend is one of none, overhead, approaching, receding, steady. */
data class Storm(val last: Strike?, val strikes10m: Int, val strikes30m: Int, val strikesToday: Int, val trend: String)

/**
 * history, week and storm come from newer versions of Weather Lab (0.32 and up). With an older Weather Lab they are
 * null and the app simply leaves those cards out.
 */
data class Snapshot(
    val status: Status,
    val current: Current,
    val today: Today,
    val alerts: List<Alert>,
    val history: History? = null,
    val week: Week? = null,
    val storm: Storm? = null,
    val tiles: Tiles? = null,
    val airport: Airport? = null,
    val home: HomeLayout? = null,
)

/**
 * How Home is laid out on the PC (Weather Lab 0.37.46 and up), so the Live tab can follow it: whether the briefing
 * shows, the reading cards in their order and which are wide. Null with an older Weather Lab, which keeps the old order.
 */
data class HomeLayout(val briefing: Boolean, val cards: List<String>, val wide: Set<String>)

/**
 * What the Live tiles show besides the latest reading. From Weather Lab 0.37.8 and up; an older Weather Lab leaves
 * it null and the tiles simply have no trend lines or peaks.
 */
data class MeasureHistory(
    val key: String, val days: Int, val unit: String, val points: List<Pair<Double, Double>>,
    val high: Double?, val low: Double?, val average: Double?,
)
data class Tiles(val series: Map<String, List<Double>>, val peaks: Map<String, Double?>, val pressureWord: String?)

/** One day of the 7 and 30 day charts. Missing figures are null. */
data class ChartDay(val date: String, val high: Double?, val low: Double?, val avg: Double?, val rain: Double?, val gust: Double?)
data class Charts(
    val days: List<ChartDay>, val tempUnit: String, val rainUnit: String, val windUnit: String,
    /** Temperature readings (timestamp in seconds, value): hourly for 7 days, every 3 hours for 30. */
    val tempPoints: List<Pair<Long, Double>> = emptyList(),
    /** Rain today, the last 7 days, this month and this year, keyed today, last_7_days, this_month, this_year. */
    val rainTotals: Map<String, Double?> = emptyMap(),
)
data class DayRow(val date: String, val high: Double?, val low: Double?, val rain: Double?)
data class DayList(val days: List<DayRow>, val tempUnit: String, val rainUnit: String)
data class DayValue(val label: String, val value: Double?, val unit: String)
/** Every figure kept for one day. found is false when Weather Lab has no summary for that date. */
data class DayDetail(val date: String, val found: Boolean, val estimated: Boolean, val partial: Boolean, val values: List<DayValue>)
data class RecordItem(val label: String, val value: Double?, val unit: String, val date: String, val estimated: Boolean)

/** One day with one figure, in the lists on Records and analysis. */
data class EventDay(val date: String, val value: Double?)
data class BoardRow(val label: String, val value: Double?, val unit: String, val whenText: String, val isNew: Boolean, val high: Boolean)
data class BoardGroup(val title: String, val rows: List<BoardRow>)
data class DistBucket(val from: Double?, val count: Int)

/** Everything on Records and analysis: summary tiles, event lists, the daily-high distribution and the records board. */
data class RecordsAnalysis(
    val tempUnit: String, val gustUnit: String, val rainUnit: String,
    val days100: Int, val days110: Int, val hottest: EventDay?, val gust: EventDay?, val rainTotal: Double?, val rainDays: Int,
    val heat: List<EventDay>, val cold: List<EventDay>, val wind: List<EventDay>, val rain: List<EventDay>,
    val heatCount: Int, val coldCount: Int, val windCount: Int, val rainCount: Int,
    val heatLimit: Double?, val coldLimit: Double?, val windLimit: Double?, val rainLimit: Double?,
    val distribution: List<DistBucket>, val board: List<BoardGroup>,
)

data class RankItem(val date: String, val value: Double?)
data class Ranking(val label: String, val unit: String, val items: List<RankItem>)
data class RainDay(val date: String, val rain: Double?)
data class RainYear(val year: Int, val total: Double?, val rainyDays: Int)
data class RainMonth(
    val month: String, val days: List<RainDay>, val total: Double?, val rainyDays: Int, val unit: String,
    val years: List<RainYear>,
)
data class RainEvent(val start: Long, val end: Long, val minutes: Int, val rain: Double?, val peakRate: Double?)
data class RainEvents(val events: List<RainEvent>, val rainUnit: String, val rateUnit: String)
data class WindDir(val name: String, val bins: List<Double>, val percent: Double)
data class WindRose(
    val days: Int, val total: Int, val dominant: String?, val bins: List<String>, val directions: List<WindDir>,
    val average: Double?, val strongestGust: Double?, val unit: String,
    /** Share of readings under 1 mph, counted in the total but not drawn. Needs Weather Lab 0.37.24. */
    val calmPercent: Double? = null,
)
data class LightningDay(val date: String, val strikes: Int, val closest: Double?)
data class LightningDays(val days: List<LightningDay>, val unit: String)
data class Burn(val skin: String, val minutes: Int?)
data class SunInfo(
    val uvNow: Double?, val uvNowWord: String?, val uvPeak: Double?, val uvPeakWord: String?,
    val sunshineToday: Double, val sunshineWeek: Double, val sunrise: String?, val sunset: String?,
    val burnNow: List<Burn>, val burnPeak: List<Burn>,
)
data class HeatLevel(val level: String, val from: Double?, val advice: String)
data class HeatHours(val level: String, val hours: Double)
data class HeatDangerDay(val date: String, val value: Double?, val level: String)
/** One point on today's heat chart: time in seconds, air temperature and heat index. */
data class HeatPoint(val time: Long, val temp: Double?, val heatIndex: Double?)
data class HeatInfo(
    val heatIndex: Double?, val level: String, val advice: String, val temp: Double?, val peakToday: Double?,
    val peakTime: String?, val days100: Int, val days105: Int, val days110: Int, val year: Int,
    val levels: List<HeatLevel>, val unit: String,
    // Added in Weather Lab 0.37.21; empty or zero on older versions.
    val hours: List<HeatHours> = emptyList(), val dangerHours: Double = 0.0, val dangerLimit: Double? = null,
    val dangerDays: List<HeatDangerDay> = emptyList(), val series: List<HeatPoint> = emptyList(),
)
data class SensorHealth(val label: String, val presentPercent: Double, val ageSeconds: Long?)
data class Radio(val name: String, val battery: String, val batteryState: String, val signal: String, val signalState: String)
data class Gap(val start: Long, val end: Long, val minutes: Int)
data class StationInfo(
    val status: String, val statusText: String, val completeness: Double, val gapCount: Int, val longestGap: Int,
    val gaps: List<Gap>, val sensors: List<SensorHealth>, val radios: List<Radio>?, val warnings: List<String>,
)
data class NewsEntry(val version: String, val date: String, val items: List<String>)
data class FcPeriod(
    val name: String, val isDay: Boolean, val temp: Int?, val short: String, val detail: String,
    val rainChance: Int?, val wind: String,
)
data class FcHour(val start: String, val temp: Int?, val short: String, val rainChance: Int?)
data class FcAlert(val event: String, val severity: String, val headline: String)
data class Forecast(
    val available: Boolean, val reason: String, val unit: String, val periods: List<FcPeriod>,
    val hours: List<FcHour>, val alerts: List<FcAlert>,
)

// History pages (Weather Lab 0.37 and newer)
data class OtdYear(val year: Int, val high: Double?, val low: Double?, val rain: Double?)
data class OnThisDay(
    val month: Int, val day: Int, val years: List<OtdYear>, val recordHigh: Double?, val recordLow: Double?,
    val tempUnit: String, val rainUnit: String, val error: String?,
)
/** One month row of the comparison: year -> (average temperature, rain). */
data class CmpMonth(val label: String, val values: Map<String, Pair<Double?, Double?>>)
data class HeatYear(val year: Int, val days100: Int, val days110: Int)
data class Compare(
    val years: List<Int>, val months: List<CmpMonth>, val rainToDate: List<Pair<Int, Double?>>, val heat: List<HeatYear>,
    val tempUnit: String, val rainUnit: String,
)
data class Extreme(val value: Double?, val date: String)
data class DayBrief(val date: String, val high: Double?, val low: Double?, val rain: Double?)
data class MonthCard(
    val month: String, val found: Boolean, val daysWithData: Int, val averageHigh: Double?, val averageLow: Double?,
    val hottest: Extreme?, val coldest: Extreme?, val wettest: Extreme?, val windiest: Extreme?,
    val rainTotal: Double?, val rainyDays: Int, val daily: List<DayBrief>,
    val tempUnit: String, val rainUnit: String, val windUnit: String, val error: String?,
)
data class Run(val days: Int, val from: String, val to: String)
data class YearCard(
    val year: Int, val found: Boolean, val daysWithData: Int, val rain: Double?, val rainyDays: Int,
    val hottest: Extreme?, val coldest: Extreme?, val windiest: Extreme?,
    val days90: Int, val days100: Int, val freezingNights: Int, val longestDry: Run?, val longestWet: Run?,
    val years: List<Int>, val tempUnit: String, val rainUnit: String, val windUnit: String,
    val wettest: Extreme? = null, val longestHot: Run? = null,
)
/** One figure of the daily briefing, already written out ("108°F", "10%", "S 10 mph"); [value] is null when there is no data. */
data class BriefingTile(val label: String, val value: String?, val note: String?)
data class Briefing(val date: String, val headline: String, val forecast: Boolean, val tiles: List<BriefingTile>, val notes: List<String>)
data class MsYear(val year: Int, val first: String, val last: String, val count: Int)
data class Milestone(val label: String, val years: List<MsYear>)
data class Anomaly(val label: String, val kind: String, val value: Double?, val usual: Double?, val unit: String, val time: String)
data class Anomalies(val items: List<Anomaly>, val checked: List<String>)
data class FaRow(val date: String, val forecast: Double?, val actual: Double?, val miss: Double?)
data class ForecastAccuracy(val days: Int, val averageMiss: Double?, val bias: Double?, val rows: List<FaRow>, val unit: String)
data class CsvExport(val filename: String, val csv: String)
data class RwSeries(val label: String, val unit: String, val points: List<Pair<Long, Double>>)
data class Rewind(val date: String, val found: Boolean, val series: List<RwSeries>, val error: String?)

// Public-source pages
data class ApRow(val label: String, val station: Double?, val airport: Double?, val unit: String)
data class Airport(
    val available: Boolean, val reason: String, val name: String, val isAirport: Boolean, val distance: Double?,
    val distanceUnit: String, val time: String, val text: String, val rows: List<ApRow>,
    val visibility: Measure? = null, val present: List<String> = emptyList(),
)
data class TropicalStorm(val name: String, val cls: String, val wind: Double?, val distance: Double?, val direction: String, val move: String)
data class Tropics(
    val available: Boolean, val reason: String, val ok: Boolean, val checked: String, val storms: List<TropicalStorm>,
    val windUnit: String, val distanceUnit: String,
)
data class RadarInfo(
    val available: Boolean, val reason: String, val usImage: String, val usPage: String,
    val localImage: String?, val localLoop: String?, val localPage: String?, val station: String?,
)
data class Pollutant(val label: String, val aqi: Int)
data class AirInfo(
    val available: Boolean, val reason: String, val aqi: Int, val category: String, val area: String, val pollutants: List<Pollutant>,
)
data class PushInfo(val server: String, val topic: String)

class ApiException(message: String, val code: Int = 0) : Exception(message)

/** Talks to Weather Lab's read-only API (api_version 1). Every call is a plain GET with the token in the header. */
class WeatherLabApi(address: String, private val token: String, private val saved: Map<String, String>? = null) {

    private val base: String = normalise(address)

    /** The raw answer to each request that worked, by path, so the phone can keep the last snapshot for offline use. */
    val recorded = linkedMapOf<String, String>()

    private fun get(path: String): JSONObject {
        // Built from saved answers (offline mode): no network.
        if (saved != null) return JSONObject(saved[path] ?: throw ApiException("Not saved.", 0))
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw ApiException(json.optString("error", "Weather Lab answered with status $code."), code)
            }
            if (path in SAVED_PATHS) recorded[path] = text
            return json
        } finally {
            conn.disconnect()
        }
    }

    fun status(): Status {
        val j = get("/api/v1/status")
        return Status(
            station = j.optString("station"),
            appVersion = j.optString("app_version"),
            ageSeconds = if (j.isNull("latest_reading_age_seconds")) null else j.getInt("latest_reading_age_seconds"),
        )
    }

    fun current(): Current {
        val j = get("/api/v1/current")
        val metric = j.optBoolean("metric")
        val reading = j.optJSONObject("reading") ?: return Current(null, emptyMap(), metric)
        val values = reading.getJSONObject("values")
        val map = LinkedHashMap<String, Measure>()
        values.keys().forEach { key ->
            val item = values.getJSONObject(key)
            map[key] = Measure(item.getDouble("value"), item.optString("unit"))
        }
        val words = LinkedHashMap<String, String>()
        reading.optJSONObject("words")?.let { w -> w.keys().forEach { k -> w.optString(k).takeIf { it.isNotBlank() }?.let { words[k] = it } } }
        return Current(reading.optInt("age_seconds"), map, metric, words)
    }

    fun today(): Today {
        val j = get("/api/v1/today")
        fun measure(name: String): Measure? =
            j.optJSONObject(name)?.let { Measure(it.getDouble("value"), it.optString("unit")) }
        return Today(j.optString("date"), measure("high"), measure("low"), measure("rain"), j.optInt("readings"))
    }

    fun alerts(): List<Alert> {
        val list = get("/api/v1/alerts").optJSONArray("alerts") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            Alert(item.getLong("ts"), item.getString("text"))
        }
    }

    fun history(): History {
        val j = get("/api/v1/history")
        val list = j.optJSONArray("points")
        val points = if (list == null) emptyList() else (0 until list.length()).mapNotNull { i ->
            val p = list.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2 || p.isNull(1)) null else Pair(p.getLong(0), p.getDouble(1))
        }
        return History(j.optString("unit"), points)
    }

    fun week(): Week {
        val j = get("/api/v1/week")
        val list = j.optJSONArray("days")
        fun num(o: JSONObject, key: String): Double? = if (o.isNull(key)) null else o.optDouble(key)
        val days = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            WeekDay(d.optString("date"), num(d, "high"), num(d, "low"), num(d, "rain"))
        }
        return Week(days, j.optString("temp_unit"), j.optString("rain_unit"))
    }

    fun storm(): Storm {
        val j = get("/api/v1/storm")
        val last = j.optJSONObject("last_strike")?.let {
            val dist = it.getJSONObject("distance")
            Strike(it.getLong("ts"), it.optLong("age_seconds"), Measure(dist.getDouble("value"), dist.optString("unit")))
        }
        return Storm(
            last, j.optInt("strikes_10m"), j.optInt("strikes_30m"), j.optInt("strikes_today"),
            j.optString("trend", "none"),
        )
    }

    private fun JSONObject.num(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key)

    /** days is 7 or 30, or null for every day kept. All needs Weather Lab 0.37.22 or newer. */
    fun charts(days: Int?): Charts {
        val j = get("/api/v1/charts/${days ?: "all"}")
        val list = j.optJSONArray("days")
        val out = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            ChartDay(d.optString("date"), d.num("high"), d.num("low"), d.num("avg"), d.num("rain"), d.num("gust"))
        }
        val pts = j.optJSONArray("temp_points")
        val points = if (pts == null) emptyList() else (0 until pts.length()).mapNotNull { i ->
            val p = pts.optJSONArray(i) ?: return@mapNotNull null
            if (p.length() < 2 || p.isNull(1)) null else Pair(p.getLong(0), p.getDouble(1))
        }
        val rt = j.optJSONObject("rain_totals")
        val totals = if (rt == null) emptyMap() else rt.keys().asSequence().associateWith { rt.num(it) }
        return Charts(out, j.optString("temp_unit"), j.optString("rain_unit"), j.optString("wind_unit"), points, totals)
    }

    fun days(): DayList {
        val j = get("/api/v1/days")
        val list = j.optJSONArray("days")
        val out = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val d = list.getJSONObject(i)
            DayRow(d.optString("date"), d.num("high"), d.num("low"), d.num("rain"))
        }
        return DayList(out, j.optString("temp_unit"), j.optString("rain_unit"))
    }

    /** which is "yesterday" or a date such as 2026-10-20. */
    fun day(which: String): DayDetail {
        val j = get("/api/v1/days/$which")
        val list = j.optJSONArray("values")
        val values = if (list == null) emptyList() else (0 until list.length()).map { i ->
            val v = list.getJSONObject(i)
            DayValue(v.optString("label"), v.num("value"), v.optString("unit"))
        }
        return DayDetail(
            j.optString("date"), j.optBoolean("found"), j.optBoolean("estimated"), j.optBoolean("partial"), values,
        )
    }

    fun records(): List<RecordItem> {
        val list = get("/api/v1/records").optJSONArray("records") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val r = list.getJSONObject(i)
            RecordItem(r.optString("label"), r.num("value"), r.optString("unit"), r.optString("date"), r.optBoolean("estimated"))
        }
    }

    /** Needs Weather Lab 0.37.20 or newer. */
    fun recordsAnalysis(): RecordsAnalysis {
        val j = get("/api/v1/records/analysis")
        val u = j.optJSONObject("units") ?: JSONObject()
        val sm = j.optJSONObject("summary") ?: JSONObject()
        val c = j.optJSONObject("counts") ?: JSONObject()
        val ev = j.optJSONObject("events") ?: JSONObject()
        val lim = j.optJSONObject("limits") ?: JSONObject()
        fun day(o: JSONObject?) = if (o == null) null else EventDay(o.optString("date"), o.num("value"))
        fun list(key: String) = ev.each(key) { EventDay(it.optString("date"), it.num("value")) }
        return RecordsAnalysis(
            u.optString("temp"), u.optString("gust"), u.optString("rain"),
            sm.optInt("days100"), sm.optInt("days110"), day(sm.optJSONObject("hottest")), day(sm.optJSONObject("gust")),
            sm.num("rainTotal"), sm.optInt("rainDays"),
            list("heat"), list("cold"), list("wind"), list("rain"),
            c.optInt("heat"), c.optInt("cold"), c.optInt("wind"), c.optInt("rain"),
            lim.num("heat"), lim.num("cold"), lim.num("wind"), lim.num("rain"),
            j.each("distribution") { DistBucket(it.num("from"), it.optInt("count")) },
            j.each("board") { g ->
                BoardGroup(g.optString("title"), g.each("rows") { r ->
                    BoardRow(r.optString("label"), r.num("value"), r.optString("unit"), r.optString("when"),
                        r.optBoolean("new"), r.optBoolean("high"))
                })
            },
        )
    }

    private fun JSONObject.int(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)

    private fun <T> JSONObject.each(key: String, f: (JSONObject) -> T): List<T> {
        val list = optJSONArray(key) ?: return emptyList()
        return (0 until list.length()).map { f(list.getJSONObject(it)) }
    }

    fun rankings(): List<Ranking> = get("/api/v1/rankings").each("rankings") { r ->
        Ranking(r.optString("label"), r.optString("unit"), r.each("items") { RankItem(it.optString("date"), it.num("value")) })
    }

    /** month is like 2026-10. */
    fun rainMonth(month: String): RainMonth {
        val j = get("/api/v1/rain/calendar/$month")
        return RainMonth(
            j.optString("month"), j.each("days") { RainDay(it.optString("date"), it.num("rain")) },
            j.num("total"), j.optInt("rainy_days"), j.optString("unit"),
            j.each("years") { RainYear(it.optInt("year"), it.num("total"), it.optInt("rainy_days")) },
        )
    }

    fun rainEvents(): RainEvents {
        val j = get("/api/v1/rain/events")
        return RainEvents(
            j.each("events") { RainEvent(it.getLong("start"), it.getLong("end"), it.optInt("minutes"), it.num("rain"), it.num("peak_rate")) },
            j.optString("rain_unit"), j.optString("rate_unit"),
        )
    }

    /** days is 1, 7 or 30, or null for all of it (Weather Lab 0.37.22 or newer). */
    fun windRose(days: Int?): WindRose {
        val j = get("/api/v1/wind/${days ?: "all"}")
        val bins = j.optJSONArray("bins")
        return WindRose(
            days ?: 0, j.optInt("total"), if (j.isNull("dominant")) null else j.optString("dominant"),
            if (bins == null) emptyList() else (0 until bins.length()).map { bins.getString(it) },
            j.each("directions") { d ->
                val b = d.optJSONArray("bins")
                WindDir(d.optString("name"), if (b == null) emptyList() else (0 until b.length()).map { b.getDouble(it) }, d.optDouble("percent"))
            },
            j.num("average"), j.num("strongest_gust"), j.optString("unit"), j.num("calm_percent"),
        )
    }

    fun lightningDays(): LightningDays {
        val j = get("/api/v1/lightning/days")
        return LightningDays(j.each("days") { LightningDay(it.optString("date"), it.optInt("strikes"), it.num("closest")) }, j.optString("unit"))
    }

    fun sun(): SunInfo {
        val j = get("/api/v1/sun")
        fun burns(key: String) = j.each(key) { Burn(it.optString("skin"), it.int("minutes")) }
        return SunInfo(
            j.num("uv_now"), if (j.isNull("uv_now_word")) null else j.optString("uv_now_word"),
            j.num("uv_peak"), if (j.isNull("uv_peak_word")) null else j.optString("uv_peak_word"),
            j.optDouble("sunshine_today_hours"), j.optDouble("sunshine_week_hours"),
            if (j.isNull("sunrise")) null else j.optString("sunrise"), if (j.isNull("sunset")) null else j.optString("sunset"),
            burns("burn_now"), burns("burn_peak"),
        )
    }

    fun heat(): HeatInfo {
        val j = get("/api/v1/heat")
        return HeatInfo(
            j.num("heat_index"), j.optString("level"), j.optString("advice"), j.num("temp"), j.num("peak_today"),
            if (j.isNull("peak_time")) null else j.optString("peak_time"),
            j.optInt("days_100"), j.optInt("days_105"), j.optInt("days_110"), j.optInt("year"),
            j.each("levels") { HeatLevel(it.optString("level"), it.num("from"), it.optString("advice")) }, j.optString("unit"),
            j.each("hours") { HeatHours(it.optString("level"), it.optDouble("hours", 0.0)) },
            j.optDouble("danger_hours", 0.0), j.num("danger_limit"),
            j.each("danger_days") { HeatDangerDay(it.optString("date"), it.num("value"), it.optString("level")) },
            (j.optJSONArray("series")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONArray(i)?.let { p -> HeatPoint(p.optLong(0), if (p.isNull(1)) null else p.optDouble(1), if (p.isNull(2)) null else p.optDouble(2)) }
                }
            } ?: emptyList()),
        )
    }

    fun station(): StationInfo {
        val j = get("/api/v1/station")
        val radios = if (j.isNull("radios")) null else j.each("radios") {
            Radio(it.optString("name"), it.optString("battery"), it.optString("battery_state"), it.optString("signal"), it.optString("signal_state"))
        }
        val warnings = j.optJSONArray("warnings")
        return StationInfo(
            j.optString("status"), j.optString("status_text"), j.optDouble("completeness_percent"),
            j.optInt("gap_count"), j.optInt("longest_gap_minutes"),
            j.each("gaps") { Gap(it.getLong("start"), it.getLong("end"), it.optInt("minutes")) },
            j.each("sensors") { SensorHealth(it.optString("label"), it.optDouble("present_percent"), if (it.isNull("age_seconds")) null else it.optLong("age_seconds")) },
            radios, if (warnings == null) emptyList() else (0 until warnings.length()).map { warnings.getString(it) },
        )
    }

    fun whatsNew(): List<NewsEntry> = get("/api/v1/whatsnew").each("entries") { e ->
        val items = e.optJSONArray("items")
        NewsEntry(e.optString("version"), e.optString("date"), if (items == null) emptyList() else (0 until items.length()).map { items.getString(it) })
    }

    fun forecast(): Forecast {
        val j = get("/api/v1/forecast")
        return Forecast(
            j.optBoolean("available"), j.optString("reason"), j.optString("unit"),
            j.each("periods") { FcPeriod(it.optString("name"), it.optBoolean("is_day"), it.int("temp"), it.optString("short"), it.optString("detail"), it.int("rain_chance"), it.optString("wind")) },
            j.each("hours") { FcHour(it.optString("start"), it.int("temp"), it.optString("short"), it.int("rain_chance")) },
            j.each("alerts") { FcAlert(it.optString("event"), it.optString("severity"), it.optString("headline")) },
        )
    }

    private fun JSONObject.text(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.extreme(key: String): Extreme? =
        optJSONObject(key)?.let { Extreme(it.num("value"), it.optString("date")) }

    private fun JSONObject.runOf(key: String): Run? =
        optJSONObject(key)?.let { Run(it.optInt("days"), it.optString("from"), it.optString("to")) }

    private fun JSONObject.ints(key: String): List<Int> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getInt(it) }
    }

    private fun JSONObject.strings(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    /** which is "today" or a month and day such as 10-23. */
    fun onThisDay(which: String): OnThisDay {
        val j = get("/api/v1/onthisday/$which")
        return OnThisDay(
            j.optInt("month"), j.optInt("day"),
            j.each("years") { OtdYear(it.optInt("year"), it.num("high"), it.num("low"), it.num("rain")) },
            j.num("record_high"), j.num("record_low"), j.optString("temp_unit"), j.optString("rain_unit"), j.text("error"),
        )
    }

    fun compare(): Compare {
        val j = get("/api/v1/compare")
        return Compare(
            j.ints("years"),
            j.each("months") { m ->
                val v = m.optJSONObject("values")
                val map = LinkedHashMap<String, Pair<Double?, Double?>>()
                v?.keys()?.forEach { y -> v.getJSONObject(y).let { map[y] = Pair(it.num("temp"), it.num("rain")) } }
                CmpMonth(m.optString("label"), map)
            },
            j.each("rain_to_date") { Pair(it.optInt("year"), it.num("rain")) },
            j.each("heat_days") { HeatYear(it.optInt("year"), it.optInt("days_100"), it.optInt("days_110")) },
            j.optString("temp_unit"), j.optString("rain_unit"),
        )
    }

    /** month is like 2026-10. */
    fun monthCard(month: String): MonthCard {
        val j = get("/api/v1/month/$month")
        return MonthCard(
            month, j.optBoolean("found"), j.optInt("days_with_data"), j.num("average_high"), j.num("average_low"),
            j.extreme("hottest"), j.extreme("coldest"), j.extreme("wettest"), j.extreme("windiest"),
            j.num("rain_total"), j.optInt("rainy_days"),
            j.each("daily") { DayBrief(it.optString("date"), it.num("high"), it.num("low"), it.num("rain")) },
            j.optString("temp_unit"), j.optString("rain_unit"), j.optString("wind_unit"), j.text("error"),
        )
    }

    fun yearCard(year: Int): YearCard {
        val j = get("/api/v1/year/$year")
        return YearCard(
            year, j.optBoolean("found"), j.optInt("days_with_data"), j.num("rain"), j.optInt("rainy_days"),
            j.extreme("hottest"), j.extreme("coldest"), j.extreme("windiest"),
            j.optInt("days_90"), j.optInt("days_100"), j.optInt("freezing_nights"), j.runOf("longest_dry"), j.runOf("longest_wet"),
            j.ints("years"), j.optString("temp_unit"), j.optString("rain_unit"), j.optString("wind_unit"),
            j.extreme("wettest"), j.runOf("longest_hot"),
        )
    }

    /** Today at a glance (needs Weather Lab 0.37.26 or newer). */
    fun briefing(): Briefing {
        val j = get("/api/v1/briefing")
        return Briefing(
            j.optString("date"), j.optString("headline"), j.optBoolean("forecast"),
            j.each("tiles") { t ->
                val unit = t.optString("unit")
                val value = if (!t.has("value") || t.isNull("value")) null else {
                    val raw = t.get("value")
                    val text = if (raw is Number) (if (raw.toDouble() % 1.0 == 0.0) raw.toLong().toString() else "%.1f".format(raw.toDouble())) else raw.toString()
                    if (unit == "%" || unit.startsWith("°")) text + unit else if (unit.isBlank()) text else "$text $unit"
                }
                BriefingTile(t.optString("label"), value, t.text("note"))
            },
            j.strings("notes"),
        )
    }

    fun milestones(): List<Milestone> = get("/api/v1/milestones").each("milestones") { m ->
        Milestone(m.optString("label"), m.each("years") { MsYear(it.optInt("year"), it.optString("first"), it.optString("last"), it.optInt("count")) })
    }

    fun anomalies(): Anomalies {
        val j = get("/api/v1/anomalies")
        return Anomalies(
            j.each("anomalies") { Anomaly(it.optString("label"), it.optString("kind"), it.num("value"), it.num("usual"), it.optString("unit"), it.optString("time")) },
            j.strings("checked"),
        )
    }

    fun alertHistory(): List<Alert> = get("/api/v1/alerts/history").each("alerts") { Alert(it.getLong("ts"), it.getString("text")) }

    fun forecastAccuracy(): ForecastAccuracy {
        val j = get("/api/v1/forecast/accuracy")
        return ForecastAccuracy(
            j.optInt("days"), j.num("average_miss"), j.num("bias"),
            j.each("rows") { FaRow(it.optString("date"), it.num("forecast"), it.num("actual"), it.num("miss")) }, j.optString("unit"),
        )
    }

    fun exportDaily(): CsvExport {
        val j = get("/api/v1/export/daily")
        return CsvExport(j.optString("filename", "weatherlab-daily.csv"), j.optString("csv"))
    }

    /** date is like 2026-10-20. */
    fun rewind(date: String): Rewind {
        val j = get("/api/v1/rewind/$date")
        return Rewind(
            date, j.optBoolean("found"),
            j.each("series") { s ->
                val pts = s.optJSONArray("points")
                RwSeries(
                    s.optString("label"), s.optString("unit"),
                    if (pts == null) emptyList() else (0 until pts.length()).mapNotNull { i ->
                        val p = pts.optJSONArray(i) ?: return@mapNotNull null
                        if (p.length() < 2 || p.isNull(1)) null else Pair(p.getLong(0), p.getDouble(1))
                    },
                )
            },
            j.text("error"),
        )
    }

    fun airport(): Airport {
        val j = get("/api/v1/airport")
        return Airport(
            j.optBoolean("available"), j.optString("reason"), j.optString("name"), j.optBoolean("airport"), j.num("distance"),
            j.optString("distance_unit"), j.optString("time"), j.optString("text"),
            j.each("rows") { ApRow(it.optString("label"), it.num("station"), it.num("airport"), it.optString("unit")) },
            visibility = j.optJSONObject("visibility")?.let { Measure(it.optDouble("value"), it.optString("unit")) },
            present = j.strings("present"),
        )
    }

    fun measureHistory(key: String, days: Int): MeasureHistory {
        val j = get("/api/v1/measure/$key/$days")
        val a = j.optJSONArray("points")
        val pts = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
            a.optJSONArray(i)?.let { p -> if (p.isNull(1)) null else Pair(p.optDouble(0), p.optDouble(1)) }
        }
        return MeasureHistory(key, days, j.optString("unit"), pts, j.num("high"), j.num("low"), j.num("average"))
    }

    fun tiles(): Tiles {
        val j = get("/api/v1/tiles")
        val series = HashMap<String, List<Double>>()
        j.optJSONObject("series")?.let { o ->
            o.keys().forEach { k ->
                val a = o.optJSONArray(k) ?: return@forEach
                series[k] = (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { p -> if (p.isNull(1)) null else p.optDouble(1) } }
            }
        }
        val peaks = HashMap<String, Double?>()
        j.optJSONObject("peaks")?.let { o -> o.keys().forEach { k -> peaks[k] = o.num(k) } }
        return Tiles(series, peaks, j.optJSONObject("pressure")?.optString("word"))
    }

    fun tropics(): Tropics {
        val j = get("/api/v1/tropics")
        return Tropics(
            j.optBoolean("available"), j.optString("reason"), j.optBoolean("ok", true), j.optString("checked"),
            j.each("storms") { TropicalStorm(it.optString("name"), it.optString("class"), it.num("wind"), it.num("distance"), it.optString("direction"), it.optString("move")) },
            j.optString("wind_unit"), j.optString("distance_unit"),
        )
    }

    fun radar(): RadarInfo {
        val j = get("/api/v1/radar")
        return RadarInfo(
            j.optBoolean("available"), j.optString("reason"), j.optString("us_image"), j.optString("us_page"),
            j.text("local_image"), j.text("local_loop"), j.text("local_page"), j.text("station"),
        )
    }

    fun air(): AirInfo {
        val j = get("/api/v1/air")
        return AirInfo(
            j.optBoolean("available"), j.optString("reason"), j.optInt("aqi"), j.optString("category"), j.optString("area"),
            j.each("pollutants") { Pollutant(it.optString("label"), it.optInt("aqi")) },
        )
    }

    fun home(): HomeLayout {
        val j = get("/api/v1/home")
        fun strings(key: String): List<String> =
            j.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).ifBlank { null } } } ?: emptyList()
        return HomeLayout(j.optBoolean("briefing", true), strings("cards"), strings("wide").toSet())
    }

    fun pushInfo(): PushInfo {
        val j = get("/api/v1/push")
        return PushInfo(j.optString("server"), j.optString("topic"))
    }

    /** The extra cards are optional: any trouble (an older Weather Lab, a hiccup) just leaves that card out. */
    private fun <T> optional(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    fun snapshot(): Snapshot = Snapshot(
        status(), current(), today(), alerts(),
        history = optional { history() }, week = optional { week() }, storm = optional { storm() },
        tiles = optional { tiles() }, airport = optional { airport() }, home = optional { home() },
    )

    companion object {
        /** The requests snapshot() makes; their answers are what the phone keeps for offline use. */
        private val SAVED_PATHS = setOf(
            "/api/v1/status", "/api/v1/current", "/api/v1/today", "/api/v1/alerts", "/api/v1/history", "/api/v1/week",
            "/api/v1/storm", "/api/v1/tiles", "/api/v1/airport", "/api/v1/home",
        )

        /** Accepts "192.168.1.50:8600", "http://192.168.1.50:8600" or a bare address, and returns "http://host:port". */
        fun normalise(address: String): String {
            // Also accepts a pasted full address such as http://192.168.1.50:8600/api/v1/status.
            val trimmed = address.trim().substringBefore("/api/").trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return if (withScheme.substringAfter("://").contains(":")) withScheme else "$withScheme:8600"
        }
    }
}
