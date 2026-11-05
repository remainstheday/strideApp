package dev.stride.hud

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProfileTest {

    private fun clean(json: String) = RemoteProfile.sanitise(JSONObject(json))

    @Test fun `nothing in gives nothing out`() {
        assertEquals("{}", clean("{}").toString())
        assertEquals("{}", RemoteProfile.sanitise(null).toString())
        assertEquals("{}", clean("""{"theme":{"colour":{"accent":"red"}},"bogus":1}""").toString())
    }

    @Test fun `colours are six-digit hex and nothing else`() {
        val c = clean("""{"theme":{"colour":{
            "accent":"#6da491","ink":"#FFF","bg0":"#001E20;}body{display:none","good":"rgb(1,2,3)","nope":"#000000"}}}""")
            .getJSONObject("theme").getJSONObject("colour")
        assertEquals("#6DA491", c.getString("accent"))
        assertEquals(setOf("accent"), c.keys().asSequence().toSet())
    }

    @Test fun `a look is one of the ones the page has`() {
        assertEquals("carbon", clean("""{"theme":{"base":"carbon"}}""").getJSONObject("theme").getString("base"))
        assertEquals("paper", clean("""{"theme":{"base":" paper "}}""").getJSONObject("theme").getString("base"))
        // TrailMate is the absence of one, and anything else is nothing.
        assertEquals("{}", clean("""{"theme":{"base":"trailmate"}}""").toString())
        assertEquals("{}", clean("""{"theme":{"base":"neon;}"}}""").toString())
        assertEquals("{}", clean("""{"theme":{"base":7}}""").toString())
    }

    @Test fun `numbers are clamped to what a screen survives`() {
        val t = clean("""{"theme":{"radius":{"control":99,"card":-3,"chip":"6","choice":"x"},
            "edgeWidth":2.5,"glowBlur":1e9}}""").getJSONObject("theme")
        val r = t.getJSONObject("radius")
        assertEquals(40, r.get("control"))
        assertEquals(0, r.get("card"))
        assertEquals(6, r.get("chip"))
        assertFalse(r.has("choice"))
        assertEquals(2.5, t.getDouble("edgeWidth"), 0.0)
        assertEquals(24, t.get("glowBlur"))
    }

    @Test fun `a font cannot close the rule it is written into`() {
        assertFalse(clean("""{"theme":{"font":{"family":"Inter;}*{color:red"}}}""").has("theme"))
        assertEquals("\"Inter\", sans-serif",
            clean("""{"theme":{"font":{"family":"\"Inter\", sans-serif"}}}""")
                .getJSONObject("theme").getJSONObject("font").getString("family"))
    }

    @Test fun `apps are package names, deduplicated`() {
        val a = clean("""{"apps":{"order":["com.netflix.mediaclient","com.netflix.mediaclient","not a pkg","x"],
            "hidden":["com.spotify.music"]}}""").getJSONObject("apps")
        assertEquals("""["com.netflix.mediaclient"]""", a.getJSONArray("order").toString())
        assertEquals("""["com.spotify.music"]""", a.getJSONArray("hidden").toString())
    }

    @Test fun `ways are the three known ones, once each, visible unless said otherwise`() {
        val w = clean("""{"welcome":{"ways":[{"id":"workouts","visible":false},{"id":"quick"},
            {"id":"workouts"},{"id":"secret"}]}}""").getJSONObject("welcome").getJSONArray("ways")
        assertEquals(2, w.length())
        assertEquals("workouts", w.getJSONObject(0).getString("id"))
        assertFalse(w.getJSONObject(0).getBoolean("visible"))
        assertTrue(w.getJSONObject(1).getBoolean("visible"))
    }

    @Test fun `settings are known keys only, as text`() {
        val s = clean("""{"settings":{"units":"mi","clock_24":false,"sleep_min":10,
            "default_runner":"Ann","belt_cal":"{}","whatever":"1"}}""").getJSONObject("settings")
        assertEquals(setOf("units", "clock_24", "sleep_min"), s.keys().asSequence().toSet())
        assertEquals("false", s.getString("clock_24"))
        assertEquals("10", s.getString("sleep_min"))
    }

    @Test fun `a cleaned profile is stamped and comes through a second cleaning unchanged`() {
        val once = clean("""{"name":"  Garage  ","theme":{"colour":{"accent":"#112233"}},"settings":{"units":"km"}}""")
        assertEquals(RemoteProfile.SCHEMA, once.getString("schema"))
        assertEquals("Garage", once.getString("name"))
        assertEquals(once.toString(), RemoteProfile.sanitise(JSONObject(once.toString())).toString())
    }
}
