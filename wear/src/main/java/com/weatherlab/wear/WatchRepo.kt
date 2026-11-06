package com.weatherlab.wear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One copy of the latest answers from Weather Lab, shared by the app, both tiles and every complication (0.7.28).
 * Without it each of them asked the PC on its own: with six complications that would be many requests every few
 * minutes. The answers are saved on the watch, so when the PC can't be reached the last reading is shown with its time.
 */
object WatchRepo {
    private const val FILE = "weatherlab_cache"
    private const val SLOW_MS = 30 * 60 * 1000L
    private val lock = Mutex()

    /** Thrown when the watch isn't connected to the phone app yet. */
    class NotSetUp : Exception("Open Weather Lab on your phone to connect this watch.")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The saved answers and when each was read, or empty. */
    private fun saved(context: Context): Pair<Map<String, String>, Map<String, Long>> {
        val p = prefs(context)
        val raw = mutableMapOf<String, String>()
        val at = mutableMapOf<String, Long>()
        (WatchApi.CORE + WatchApi.SLOW).forEach { name ->
            p.getString("raw_$name", null)?.let { raw[name] = it }
            at[name] = p.getLong("at_$name", 0L)
        }
        return raw to at
    }

    /** The last saved data without asking the PC, marked offline when it is older than [freshMs]. Null if nothing is saved. */
    fun cached(context: Context, freshMs: Long = Long.MAX_VALUE): WatchData? {
        val (raw, at) = saved(context)
        if (!raw.containsKey("current")) return null
        val savedAt = at["current"] ?: 0L
        val data = WatchApi.parse(raw, System.currentTimeMillis() / 1000)
        return if (System.currentTimeMillis() - savedAt > freshMs) data.copy(offlineSince = savedAt) else data
    }

    /**
     * Fresh data: the saved copy if it is younger than [maxAgeMs], otherwise asks the PC. When the PC can't be reached
     * the saved copy is returned marked offline; only with nothing saved does the error come through.
     */
    suspend fun get(context: Context, maxAgeMs: Long = 5 * 60 * 1000L, force: Boolean = false): WatchData = lock.withLock {
        val store = WatchStore(context)
        if (!store.configured) throw NotSetUp()
        val now = System.currentTimeMillis()
        val (raw, at) = saved(context)
        if (!force && raw.containsKey("current") && now - (at["current"] ?: 0L) < maxAgeMs) {
            return WatchApi.parse(raw, now / 1000)
        }
        val wanted = WatchApi.CORE + WatchApi.SLOW.filter { force || now - (at[it] ?: 0L) > SLOW_MS }
        try {
            val fresh = withContext(Dispatchers.IO) { WatchApi.fetch(store.address, store.token, wanted) }
            val edit = prefs(context).edit()
            fresh.forEach { (name, text) ->
                edit.putString("raw_$name", text).putLong("at_$name", now)
            }
            edit.apply()
            WatchApi.parse(raw + fresh, now / 1000)
        } catch (e: Exception) {
            if (!raw.containsKey("current")) throw e
            WatchApi.parse(raw, now / 1000).copy(offlineSince = at["current"] ?: 0L)
        }
    }

    /** Forgets the saved answers, for a new PC address. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
