package dev.stride.hud

import org.json.JSONArray
import org.json.JSONObject

/**
 * A console profile sent from a desktop over adb: the look, the order of the
 * apps under APPS, which ways in the welcome screen offers, and a handful of
 * console settings. See docs/REMOTE.md.
 *
 * Everything here is cleaned before it is stored or shown, because the page
 * writes these values straight into a stylesheet: a colour is `#rrggbb` and
 * nothing else, a number is clamped to a range a screen can survive, and a
 * font name cannot close the rule it is put in. Anything not in the schema is
 * dropped rather than passed on, so an older console ignores a newer desktop
 * instead of half-applying it.
 *
 * Nothing in a profile reaches the belt. Speed and incline limits are
 * settings the runner already controls ([Settings.PERSONAL]); the board's own
 * limits are never in here (see [Settings]).
 */
object RemoteProfile {
    const val SCHEMA = "stride.profile.v1"

    val COLOURS = listOf(
        "bg0", "bg1", "ink", "inkDim", "inkFaint", "accent", "accentWarm", "good", "alert", "trackLine",
    )
    val RADII = listOf("control", "chip", "card", "choice", "floating")

    /** The welcome screen's ways in, in their default order (HowStep.tsx). */
    val WAYS = listOf("quick", "routes", "workouts")

    /** Settings a profile may carry. Who runs by default is the console's
     *  people list's business, not a profile's. */
    val SETTINGS: Set<String> get() = Settings.KNOWN - Settings.DEFAULT_RUNNER

    private val HEX = Regex("^#[0-9a-fA-F]{6}$")
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private const val MAX_APPS = 100

    /** [raw] cleaned to the schema. Never throws; the worst input gives `{}`. */
    fun sanitise(raw: JSONObject?): JSONObject {
        val out = JSONObject()
        if (raw == null) return out
        out.put("schema", SCHEMA)
        (raw.opt("name") as? String)?.trim()?.take(60)?.takeIf { it.isNotEmpty() }?.let { out.put("name", it) }
        raw.optJSONObject("theme")?.let(::theme)?.takeIf { it.length() > 0 }?.let { out.put("theme", it) }
        raw.optJSONObject("apps")?.let(::apps)?.takeIf { it.length() > 0 }?.let { out.put("apps", it) }
        raw.optJSONObject("welcome")?.let(::welcome)?.takeIf { it.length() > 0 }?.let { out.put("welcome", it) }
        raw.optJSONObject("settings")?.let(::settings)?.takeIf { it.length() > 0 }?.let { out.put("settings", it) }
        // Only a schema line and nothing else says nothing: store nothing.
        return if (out.length() == 1) JSONObject() else out
    }

    private fun theme(t: JSONObject): JSONObject {
        val out = JSONObject()
        t.optJSONObject("colour")?.let { c ->
            val colour = JSONObject()
            for (k in COLOURS) (c.opt(k) as? String)?.trim()?.takeIf { HEX.matches(it) }
                ?.let { colour.put(k, it.uppercase()) }
            if (colour.length() > 0) out.put("colour", colour)
        }
        t.optJSONObject("radius")?.let { r ->
            val radius = JSONObject()
            for (k in RADII) number(r.opt(k), 0.0, 40.0)?.let { radius.put(k, it) }
            if (radius.length() > 0) out.put("radius", radius)
        }
        t.optJSONObject("font")?.let { f ->
            (f.opt("family") as? String)?.trim()?.take(120)
                ?.takeIf { it.isNotEmpty() && it.none { ch -> ch in ";{}<>\\" || ch.isISOControl() } }
                ?.let { out.put("font", JSONObject().put("family", it)) }
        }
        number(t.opt("edgeWidth"), 0.0, 4.0)?.let { out.put("edgeWidth", it) }
        number(t.opt("glowBlur"), 0.0, 24.0)?.let { out.put("glowBlur", it) }
        return out
    }

    private fun apps(a: JSONObject): JSONObject {
        val out = JSONObject()
        packages(a.optJSONArray("order"))?.let { out.put("order", it) }
        packages(a.optJSONArray("hidden"))?.let { out.put("hidden", it) }
        return out
    }

    private fun packages(arr: JSONArray?): JSONArray? {
        if (arr == null) return null
        val seen = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val p = (arr.opt(i) as? String)?.trim() ?: continue
            if (p.length <= 255 && PACKAGE.matches(p)) seen += p
            if (seen.size == MAX_APPS) break
        }
        return JSONArray(seen.toList())
    }

    private fun welcome(w: JSONObject): JSONObject {
        val arr = w.optJSONArray("ways") ?: return JSONObject()
        val seen = HashSet<String>()
        val ways = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.opt("id") as? String ?: continue
            if (id !in WAYS || !seen.add(id)) continue
            ways.put(JSONObject().put("id", id).put("visible", o.opt("visible") != false))
        }
        return if (ways.length() == 0) JSONObject() else JSONObject().put("ways", ways)
    }

    /** As strings, as the page sends them; [Settings.setConsole] types them. */
    private fun settings(s: JSONObject): JSONObject {
        val out = JSONObject()
        val allowed = SETTINGS
        for (k in s.keys()) {
            if (k !in allowed) continue
            val v = s.opt(k)
            val text = when (v) {
                is String -> v
                is Boolean, is Number -> v.toString()
                else -> continue
            }
            if (text.length <= 200) out.put(k, text)
        }
        return out
    }

    /** A finite number in [min, max], whole where it is whole; else null. */
    private fun number(v: Any?, min: Double, max: Double): Number? {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        } ?: return null
        if (d.isNaN() || d.isInfinite()) return null
        val c = d.coerceIn(min, max)
        return if (c == Math.floor(c)) c.toInt() else c
    }
}
