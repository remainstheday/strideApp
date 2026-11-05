package dev.stride.hud

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A finished run (`stride.workout.v1`, see [RunRecorder]) as a TCX file, which
 * is what Strava is given.
 *
 * **Why TCX.** Strava's upload takes FIT, TCX and GPX. A treadmill has no
 * position, and of the three TCX is the one that carries a distance for every
 * second without one: a GPX with no positions has no distance at all, and FIT
 * is a binary format with a checksum that would be a file of its own here.
 * TCX is text, so it can be read back when an upload is turned down.
 *
 * **What goes in each second.** The time, the distance so far, and whatever
 * was read: heart rate, speed, cadence. The trackpoint's time is the start
 * plus `t`, which counts pauses, so a pause is a gap between two trackpoints
 * and Strava works out moving time from it the way it does for a watch.
 *
 * **Altitude is the incline, summed.** There is no altimeter either, but
 * this is an incline trainer, and a 40 % deck is most of the work. Each
 * second's rise is the ground covered at that second's grade, added up from
 * zero, and it comes back down on a decline. Strava may ignore altitude in a
 * file with no positions; the climb is written in the description as well, by
 * [Strava], so it is never lost either way.
 *
 * **Cadence is halved.** STRIDE keeps steps a minute. TCX's `RunCadence` is,
 * by Garmin's convention that everything reading it follows, one foot's:
 * strides a minute. Sent as steps, a 170 run shows as 340.
 *
 * Nothing here touches Android, so it is tested on the JVM (StravaTcxTest).
 */
object StravaTcx {

    private const val NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
    private const val EXT = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"

    /** The file. [workout] is trusted to be what [RunRecorder] builds. */
    fun build(workout: JSONObject): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val startMs = iso.parse(workout.getString("startedAt"))!!.time
        val start = iso.format(Date(startMs))
        val totals = workout.getJSONObject("totals")
        val s = workout.getJSONObject("samples")
        val t = s.getJSONArray("t")
        val dist = s.getJSONArray("distanceM")
        val speed = s.optJSONArray("speedMs")
        val incline = s.optJSONArray("inclinePct")
        val hr = s.optJSONArray("hr")
        val cadence = s.optJSONArray("cadenceSpm")

        val out = StringBuilder(64 + t.length() * 220)
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<TrainingCenterDatabase xmlns=\"$NS\" xmlns:x=\"$EXT\">\n")
        out.append("<Activities><Activity Sport=\"Running\">\n")
        out.append("<Id>$start</Id>\n")
        out.append("<Lap StartTime=\"$start\">\n")
        out.append("<TotalTimeSeconds>${totals.optLong("movingS", totals.optLong("elapsedS"))}</TotalTimeSeconds>\n")
        out.append("<DistanceMeters>${num(totals.optDouble("distanceM", 0.0), 1)}</DistanceMeters>\n")
        out.append("<Calories>${totals.optLong("calories", 0L).coerceIn(0L, 65_535L)}</Calories>\n")
        if (totals.has("avgHr")) out.append("<AverageHeartRateBpm><Value>${totals.getInt("avgHr")}</Value></AverageHeartRateBpm>\n")
        if (totals.has("maxHr")) out.append("<MaximumHeartRateBpm><Value>${totals.getInt("maxHr")}</Value></MaximumHeartRateBpm>\n")
        out.append("<Intensity>Active</Intensity><TriggerMethod>Manual</TriggerMethod>\n<Track>\n")

        var altitude = 0.0
        var lastD = 0.0
        for (i in 0 until t.length()) {
            val d = dist.optDouble(i, lastD)
            val grade = incline?.optDouble(i, 0.0) ?: 0.0
            if (i > 0) altitude += (d - lastD).coerceAtLeast(0.0) * grade / 100.0
            lastD = d
            out.append("<Trackpoint><Time>").append(iso.format(Date(startMs + t.getLong(i) * 1000L))).append("</Time>")
            out.append("<AltitudeMeters>").append(num(altitude, 1)).append("</AltitudeMeters>")
            out.append("<DistanceMeters>").append(num(d, 1)).append("</DistanceMeters>")
            int(hr, i)?.takeIf { it in 30..250 }?.let {
                out.append("<HeartRateBpm><Value>").append(it).append("</Value></HeartRateBpm>")
            }
            val v = speed?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() && it >= 0.0 }
            val strides = int(cadence, i)?.takeIf { it in 1..400 }?.let { it / 2 }
            if (v != null || strides != null) {
                out.append("<Extensions><x:TPX>")
                if (v != null) out.append("<x:Speed>").append(num(v, 2)).append("</x:Speed>")
                if (strides != null) out.append("<x:RunCadence>").append(strides).append("</x:RunCadence>")
                out.append("</x:TPX></Extensions>")
            }
            out.append("</Trackpoint>\n")
        }
        out.append("</Track>\n</Lap>\n")
        out.append("<Creator xsi:type=\"Device_t\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">")
        out.append("<Name>STRIDE</Name><UnitId>0</UnitId><ProductID>0</ProductID>")
        out.append("<Version><VersionMajor>0</VersionMajor><VersionMinor>0</VersionMinor></Version></Creator>\n")
        out.append("</Activity></Activities>\n</TrainingCenterDatabase>\n")
        return out.toString()
    }

    /** What the activity is called: the plan's name when the run had one. */
    fun name(workout: JSONObject): String =
        workout.optJSONObject("plan")?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?.take(80) ?: "Treadmill run"

    /**
     * The line under it. Strava draws no climb for a run with no positions,
     * and this machine's point is the climb, so it is said in words.
     */
    fun description(workout: JSONObject): String {
        val totals = workout.optJSONObject("totals") ?: JSONObject()
        val incline = workout.optJSONObject("samples")?.optJSONArray("inclinePct")
        val parts = ArrayList<String>()
        val climb = totals.optDouble("climbM", 0.0)
        if (climb >= 1.0) parts.add("${Math.round(climb)} m climbed")
        if (incline != null && incline.length() > 0) {
            var sum = 0.0; var max = -100.0
            for (i in 0 until incline.length()) { val g = incline.optDouble(i, 0.0); sum += g; if (g > max) max = g }
            val avg = sum / incline.length()
            if (Math.abs(avg) >= 0.05 || max >= 0.05) parts.add("incline ${num(avg, 1)} % average, ${num(max, 1)} % at its steepest")
        }
        val board = workout.optJSONObject("app")?.optString("board")?.trim().orEmpty()
        parts.add(if (board.isNotEmpty()) "Recorded by STRIDE on $board" else "Recorded by STRIDE")
        return parts.joinToString(" · ")
    }

    private fun int(a: JSONArray?, i: Int): Int? = if (a == null || a.isNull(i)) null else a.optInt(i)

    /** A number as XML wants it: a point, never a comma, whatever the console's locale. */
    private fun num(v: Double, dp: Int): String = String.format(Locale.US, "%.${dp}f", v)
}
