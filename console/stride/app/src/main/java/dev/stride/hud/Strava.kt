package dev.stride.hud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Finished runs, sent to Strava.
 *
 * Strava has no way to follow a run while it happens: its API takes a file
 * once the run is over and nothing else (docs/SYNC.md has the survey). So
 * this is the whole of it: when [RunRecorder] finishes a run for somebody who
 * has connected Strava, the run is written as a TCX file ([StravaTcx]) and
 * uploaded as an indoor run, with the incline's climb said in the
 * description.
 *
 * **Connecting is done on a desktop, not here.** Strava signs people in with
 * a web page and a redirect; it has no code-on-the-screen flow like
 * TrailMate's ([TrailMate]), and typing a password on a treadmill is the thing
 * this console refuses to ask for. So the sign-in happens in Treadmill
 * Manager on a Mac, which sends what comes out of it over adb
 * (MainActivity's `strava` remote op, docs/REMOTE.md): the Strava
 * application's id and secret, and a refresh token. The application is the
 * owner's own (strava.com/settings/api), because Strava issues those per
 * developer and a personal one may upload for its owner.
 *
 * **Those three are secrets and are kept like TrailMate's tokens**: sealed
 * with the Keystore key ([Vault]), one file a person, never in a log line,
 * a Snapshot or [statusJson]. Strava replaces the refresh token whenever it
 * likes and the old one stops working at once, so each new one is written
 * down before it is used.
 *
 * **Runs wait on disk until Strava has them**, oldest first, as TrailMate's
 * do, and for the same reasons. A run Strava says it already has is a run
 * sent. One it turns down is parked in `strava/failed`, with the reason, so
 * it does not hold up the ones behind it.
 */
class Strava(context: Context, private val cfg: Settings) {

    companion object {
        private const val TAG = "StrideStrava"

        /**
         * Strava's API. From 2027 it also answers at api-v3.strava.com and
         * wants tokens in a header, never in the form; the header is what
         * this sends already, so only this line would change.
         */
        const val API = "https://www.strava.com/api/v3"
        const val TOKEN_URL = "https://www.strava.com/oauth/token"

        const val TIMEOUT_MS = 20_000
        const val BACKOFF_FIRST_MS = 30_000L
        const val BACKOFF_MAX_MS = 15 * 60_000L

        /** Strava's own advice: ask about an upload once a second, and it is
         *  usually done within two. After this many it is asked again later. */
        const val POLLS = 30

        private val ID = Regex("^[0-9]{1,12}$")
        private val SECRET = Regex("^[0-9a-fA-F]{20,64}$")
    }

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "strava").apply { mkdirs() }
    private val queueDir = File(dir, "queue").apply { mkdirs() }
    private val failedDir = File(dir, "failed").apply { mkdirs() }
    private val statusFile = File(dir, "status.json")

    private class Link(
        val clientId: String, val clientSecret: String,
        @Volatile var refreshToken: String,
        val athlete: String,
        @Volatile var reconnect: Boolean = false,
        @Volatile var accessToken: String = "",
        @Volatile var accessUntilMs: Long = 0L,
    )

    /** person id → link. Read once, then kept in memory. */
    private val links = java.util.concurrent.ConcurrentHashMap<String, Link>().apply {
        dir.listFiles { f -> f.name.startsWith("link-") && f.name.endsWith(".bin") }?.forEach { f ->
            val id = f.name.removePrefix("link-").removeSuffix(".bin")
            read(f)?.let { put(id, it) }
        }
    }

    private fun linkFile(personId: String) = File(dir, "link-$personId.bin")

    private fun read(f: File): Link? = try {
        val o = JSONObject(Vault.open(f.readBytes()) ?: throw IOException("cannot be decrypted"))
        Link(o.getString("clientId"), o.getString("clientSecret"), o.getString("refreshToken"),
            o.optString("athlete"), o.optBoolean("reconnect"))
    } catch (e: Exception) {
        Log.w(TAG, "${f.name}: unreadable (${e.message}) — connect Strava again")
        null
    }

    /** Whole or not at all: a link half-written by a power cut is no link. */
    private fun save(personId: String, l: Link) {
        val sealed = Vault.seal(JSONObject()
            .put("clientId", l.clientId).put("clientSecret", l.clientSecret)
            .put("refreshToken", l.refreshToken).put("athlete", l.athlete)
            .put("reconnect", l.reconnect).toString())
        val tmp = File(dir, "link-$personId.tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(linkFile(personId))) throw IOException("could not save the Strava link")
    }

    fun linked(personId: String): Boolean = links.containsKey(personId)

    /**
     * Connect [personId], with what a desktop's sign-in produced. Returns
     * null, or why not. Nothing is asked of Strava here: a token that does
     * not work shows itself at the first upload, as "connect again".
     */
    fun link(personId: String, clientId: String, clientSecret: String, refreshToken: String, athlete: String): String? {
        if (!ID.matches(clientId)) return "That is not a Strava client id."
        if (!SECRET.matches(clientSecret)) return "That is not a Strava client secret."
        if (!SECRET.matches(refreshToken)) return "That is not a Strava refresh token."
        val l = Link(clientId, clientSecret, refreshToken, athlete.trim().take(60))
        return try {
            save(personId, l)
            links[personId] = l
            Log.i(TAG, "$personId: connected to Strava")
            kick()
            null
        } catch (e: Exception) {
            "Could not save the link (${e.message})."
        }
    }

    /** Forget [personId]'s link. Runs of theirs still waiting stay on the console. */
    fun unlink(personId: String) {
        links.remove(personId)
        linkFile(personId).delete()
        Log.i(TAG, "$personId: disconnected from Strava")
    }

    // --- the queue --------------------------------------------------------------

    private val lock = Object()
    private var kicked = false

    /** A finished run, from [RunRecorder]. Kept only for somebody connected. */
    fun enqueue(personId: String, workout: JSONObject) {
        if (!linked(personId)) return
        val id = workout.optString("workoutId")
        val file = File(queueDir, "${System.currentTimeMillis()}-$id.json")
        val tmp = File(queueDir, "$id.tmp")
        try {
            tmp.writeText(JSONObject().put("personId", personId).put("workout", workout).toString())
            if (!tmp.renameTo(file)) throw IOException("rename failed")
        } catch (e: Exception) {
            Log.w(TAG, "could not queue workout $id (${e.message})")
            return
        }
        note(id, "queued", "")
        Log.i(TAG, "queued workout $id for $personId")
        kick()
    }

    /** A run Strava turned down goes back in the queue. */
    fun retry(workoutId: String) {
        failedDir.listFiles { f -> f.name.endsWith("-$workoutId.json") }?.forEach {
            it.renameTo(File(queueDir, it.name))
        }
        kick()
    }

    fun kick() = synchronized(lock) { kicked = true; lock.notifyAll() }

    fun start() {
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { kick() }
            })
        } catch (e: Exception) {
            Log.w(TAG, "no network callback (${e.message}) — timer only")
        }
        thread(name = "strava", isDaemon = true) {
            var wait = 0L
            while (true) {
                synchronized(lock) {
                    if (!kicked) lock.wait(if (wait > 0) wait else BACKOFF_MAX_MS)
                    kicked = false
                }
                wait = try { drain() } catch (e: Exception) {
                    Log.w(TAG, "upload pass failed (${e.message})"); BACKOFF_FIRST_MS
                }
            }
        }
    }

    private var backoff = 0L

    /** Send what is waiting, oldest first. Returns how long to wait before
     *  trying again, or 0 when there is nothing left to try. */
    private fun drain(): Long {
        val files = queueDir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name } ?: return 0L
        for (f in files) {
            val o = try { JSONObject(f.readText()) } catch (e: Exception) { f.delete(); continue }
            val personId = o.optString("personId")
            val workout = o.optJSONObject("workout") ?: run { f.delete(); null } ?: continue
            val id = workout.optString("workoutId")
            val l = links[personId]
            // Disconnected since, or waiting to be connected again: it stays.
            if (l == null || l.reconnect) continue
            try {
                when (val r = send(l, personId, workout)) {
                    is Sent -> { f.delete(); note(id, "sent", r.activityId) ; backoff = 0L }
                    is Refused -> {
                        f.renameTo(File(failedDir, f.name))
                        note(id, "failed", r.why)
                        Log.w(TAG, "workout $id not taken by Strava: ${r.why}")
                    }
                    is Later -> {
                        note(id, "queued", r.why)
                        backoff = if (backoff == 0L) BACKOFF_FIRST_MS else minOf(backoff * 2, BACKOFF_MAX_MS)
                        return backoff
                    }
                }
            } catch (e: IOException) {
                note(id, "queued", "No connection to Strava")
                backoff = if (backoff == 0L) BACKOFF_FIRST_MS else minOf(backoff * 2, BACKOFF_MAX_MS)
                return backoff
            }
        }
        return 0L
    }

    private sealed class Outcome
    private class Sent(val activityId: String) : Outcome()
    private class Refused(val why: String) : Outcome()
    private class Later(val why: String) : Outcome()

    private fun send(l: Link, personId: String, workout: JSONObject): Outcome {
        val token = accessToken(l, personId) ?: return Later("Strava needs connecting again")
        val id = workout.optString("workoutId")
        val up = upload(token, workout, id)
        when {
            up.code == 401 -> {
                // The token was good a moment ago: once more with a new one,
                // and if that is turned away too the link itself is dead.
                l.accessToken = ""
                val again = accessToken(l, personId) ?: return Later("Strava needs connecting again")
                val second = upload(again, workout, id)
                if (second.code == 401) { reconnect(l, personId); return Later("Strava needs connecting again") }
                return settle(second, again)
            }
            else -> return settle(up, token)
        }
    }

    private fun settle(up: Reply, token: String): Outcome {
        if (up.code == 429) return Later("Strava is rate-limiting this console")
        if (up.code >= 500) return Later("Strava answered ${up.code}")
        val body = up.json()
        if (up.code !in 200..299 || body == null) {
            return Refused(body?.optString("error")?.takeIf { it.isNotEmpty() && it != "null" }
                ?: body?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Strava answered ${up.code}")
        }
        var state: JSONObject = body
        val uploadId = body.optString("id_str").ifEmpty { body.optLong("id").toString() }
        for (poll in 0 until POLLS) {
            val outcome = done(state)
            if (outcome != null) return outcome
            Thread.sleep(1_000L)
            val r = call("GET", "$API/uploads/$uploadId", token, null, null)
            if (r.code == 429 || r.code >= 500) return Later("Strava answered ${r.code}")
            state = r.json() ?: return Later("Strava's answer could not be read")
        }
        return done(state) ?: Later("Strava is still working on it")
    }

    /** An upload's status as an outcome, or null while Strava is still at it. */
    private fun done(state: JSONObject): Outcome? {
        val error = state.optString("error").takeIf { it.isNotEmpty() && it != "null" }
        if (error != null) {
            // "duplicate of activity 123": it has the run, which is all that was wanted.
            if (error.contains("duplicate", ignoreCase = true)) {
                return Sent(Regex("(\\d{5,})").find(error)?.value ?: "")
            }
            return Refused(error)
        }
        if (!state.isNull("activity_id")) return Sent(state.optLong("activity_id").toString())
        return null
    }

    private fun upload(token: String, workout: JSONObject, id: String): Reply {
        val boundary = "stride-${System.nanoTime()}"
        val body = ByteArrayOutputStream()
        fun part(name: String, value: String) = body.write(
            "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
        part("data_type", "tcx")
        part("sport_type", "Run")
        // An indoor run: no map, and Strava keeps the file's distance.
        part("trainer", "1")
        part("name", StravaTcx.name(workout))
        part("description", StravaTcx.description(workout))
        part("external_id", "stride-$id")
        body.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"stride-$id.tcx\"\r\n" +
            "Content-Type: application/vnd.garmin.tcx+xml\r\n\r\n").toByteArray(Charsets.UTF_8))
        body.write(StravaTcx.build(workout).toByteArray(Charsets.UTF_8))
        body.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        return call("POST", "$API/uploads", token, "multipart/form-data; boundary=$boundary", body.toByteArray())
    }

    // --- tokens -------------------------------------------------------------------

    /**
     * An access token for [l], asking Strava for a new one when the last has
     * under a minute left. Null when Strava no longer accepts the link, which
     * is then marked for connecting again. Throws [IOException] with no network.
     */
    private fun accessToken(l: Link, personId: String): String? {
        if (l.accessToken.isNotEmpty() && System.currentTimeMillis() < l.accessUntilMs - 60_000L) return l.accessToken
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val form = "client_id=${enc(l.clientId)}&client_secret=${enc(l.clientSecret)}" +
            "&grant_type=refresh_token&refresh_token=${enc(l.refreshToken)}"
        val r = call("POST", TOKEN_URL, null, "application/x-www-form-urlencoded", form.toByteArray(Charsets.UTF_8))
        if (r.code == 400 || r.code == 401) { reconnect(l, personId); return null }
        val o = r.json()
        val access = o?.optString("access_token").orEmpty()
        if (r.code != 200 || access.isEmpty()) throw IOException("Strava answered ${r.code} for a token")
        // Strava may hand back a new refresh token, and the old one is dead
        // from that moment: written down before anything else is done.
        val fresh = o!!.optString("refresh_token")
        if (fresh.isNotEmpty() && fresh != l.refreshToken) {
            l.refreshToken = fresh
            try { save(personId, l) } catch (e: Exception) { Log.w(TAG, "$personId: new token not saved (${e.message})") }
        }
        l.accessToken = access
        l.accessUntilMs = o.optLong("expires_at", 0L) * 1000L
        return access
    }

    private fun reconnect(l: Link, personId: String) {
        if (l.reconnect) return
        l.reconnect = true
        l.accessToken = ""
        try { save(personId, l) } catch (_: Exception) {}
        Log.w(TAG, "$personId: Strava no longer accepts this link — connect again from Treadmill Manager")
    }

    // --- status -------------------------------------------------------------------

    /** How the last run went: {workoutId, state, detail, at}. */
    @Synchronized
    private fun note(workoutId: String, state: String, detail: String) {
        try {
            statusFile.writeText(JSONObject().put("workoutId", workoutId).put("state", state)
                .put("detail", detail).put("at", System.currentTimeMillis()).toString())
        } catch (_: Exception) {}
    }

    /**
     * For the Settings page and for a desktop (docs/REMOTE.md): who is
     * connected and as whom, what is waiting, and how the last run went.
     * Never an id, a secret or a token.
     */
    fun statusJson(): String {
        val people = JSONArray()
        for (p in cfg.people()) {
            val l = links[p.id]
            people.put(JSONObject().put("id", p.id).put("name", p.name)
                .put("linked", l != null).put("athlete", l?.athlete ?: "")
                .put("reconnect", l?.reconnect == true))
        }
        val last = try { if (statusFile.isFile) JSONObject(statusFile.readText()) else null } catch (e: Exception) { null }
        return JSONObject()
            .put("people", people)
            .put("queued", queueDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0)
            .put("failed", failedDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0)
            .put("last", last ?: JSONObject.NULL)
            .toString()
    }

    // --- http -----------------------------------------------------------------------

    private class Reply(val code: Int, val body: String) {
        fun json(): JSONObject? = try { JSONObject(body) } catch (e: Exception) { null }
    }

    private fun call(method: String, url: String, token: String?, contentType: String?, body: ByteArray?): Reply {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return Reply(code, text)
        } finally {
            c.disconnect()
        }
    }
}
