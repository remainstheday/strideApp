package dev.stride.hud

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class StravaTcxTest {

    /** A run as RunRecorder builds one: [n] seconds at 3 m/s on a 10 % deck,
     *  with a pause from 4 s to 10 s. */
    private fun workout(n: Int = 6, hr: Boolean = true, plan: String? = null): JSONObject {
        val t = JSONArray(); val d = JSONArray(); val v = JSONArray(); val inc = JSONArray()
        val beats = JSONArray(); val cad = JSONArray()
        for (i in 0 until n) {
            t.put(if (i < 4) i else i + 6)
            d.put(i * 3.0); v.put(3.0); inc.put(10.0)
            beats.put(if (i == 2) JSONObject.NULL else 140 + i)
            cad.put(170)
        }
        val samples = JSONObject().put("t", t).put("distanceM", d).put("speedMs", v).put("inclinePct", inc)
            .put("cadenceSpm", cad)
        if (hr) samples.put("hr", beats)
        val totals = JSONObject().put("elapsedS", n + 5).put("movingS", n - 1)
            .put("distanceM", (n - 1) * 3.0).put("climbM", (n - 1) * 0.3).put("calories", 12)
        if (hr) totals.put("avgHr", 142).put("maxHr", 145)
        return JSONObject()
            .put("schema", "stride.workout.v1").put("workoutId", "abc")
            .put("app", JSONObject().put("name", "STRIDE").put("version", "0.8.0").put("board", "ETNT39225-INT"))
            .put("startedAt", "2026-11-04T18:00:00Z")
            .put("totals", totals)
            .put("plan", plan?.let { JSONObject().put("name", it) } ?: JSONObject.NULL)
            .put("samples", samples)
    }

    private fun parse(xml: String): Element {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())).documentElement
    }

    private fun Element.all(tag: String) = getElementsByTagNameNS("*", tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
    private fun Element.text(tag: String) = all(tag).first().textContent

    @Test fun `is well-formed TCX for one running lap`() {
        val root = parse(StravaTcx.build(workout()))
        assertEquals("TrainingCenterDatabase", root.localName)
        assertEquals("http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2", root.namespaceURI)
        val activity = root.all("Activity").single()
        assertEquals("Running", activity.getAttribute("Sport"))
        assertEquals("2026-11-04T18:00:00Z", activity.text("Id"))
        val lap = activity.all("Lap").single()
        assertEquals("2026-11-04T18:00:00Z", lap.getAttribute("StartTime"))
        assertEquals("5", lap.text("TotalTimeSeconds"))      // moving time, not elapsed
        assertEquals("15.0", lap.all("DistanceMeters").first().textContent)
        assertEquals("12", lap.text("Calories"))
        assertEquals(6, root.all("Trackpoint").size)
        assertTrue(root.all("Position").isEmpty())           // a treadmill is nowhere
    }

    @Test fun `a pause is a gap between two trackpoints`() {
        val times = parse(StravaTcx.build(workout())).all("Trackpoint").map { it.text("Time") }
        assertEquals("2026-11-04T18:00:00Z", times[0])
        assertEquals("2026-11-04T18:00:03Z", times[3])
        assertEquals("2026-11-04T18:00:10Z", times[4])
    }

    @Test fun `distance, speed and halved cadence ride on every second`() {
        val pts = parse(StravaTcx.build(workout())).all("Trackpoint")
        assertEquals(listOf("0.0", "3.0", "6.0", "9.0", "12.0", "15.0"), pts.map { it.text("DistanceMeters") })
        assertEquals("3.00", pts[1].text("Speed"))
        assertEquals("85", pts[1].text("RunCadence"))        // 170 steps is 85 strides
    }

    @Test fun `altitude is the incline added up`() {
        val alt = parse(StravaTcx.build(workout())).all("Trackpoint").map { it.text("AltitudeMeters") }
        assertEquals(listOf("0.0", "0.3", "0.6", "0.9", "1.2", "1.5"), alt)
    }

    @Test fun `a second with no heartbeat has none, and a run with no strap has none at all`() {
        val pts = parse(StravaTcx.build(workout())).all("Trackpoint")
        assertEquals("141", pts[1].all("HeartRateBpm").single().text("Value"))
        assertTrue(pts[2].all("HeartRateBpm").isEmpty())
        val bare = parse(StravaTcx.build(workout(hr = false)))
        assertTrue(bare.all("HeartRateBpm").isEmpty())
        assertTrue(bare.all("AverageHeartRateBpm").isEmpty())
    }

    @Test fun `is named for its plan, and says the climb in words`() {
        assertEquals("Treadmill run", StravaTcx.name(workout()))
        assertEquals("Hill repeats", StravaTcx.name(workout(plan = "Hill repeats")))
        val d = StravaTcx.description(workout(n = 101))
        assertEquals("30 m climbed · incline 10.0 % average, 10.0 % at its steepest · Recorded by STRIDE on ETNT39225-INT", d)
        // A flat run says only who recorded it.
        val flat = workout().apply {
            getJSONObject("totals").put("climbM", 0)
            getJSONObject("samples").put("inclinePct", JSONArray(List(6) { 0.0 }))
        }
        assertEquals("Recorded by STRIDE on ETNT39225-INT", StravaTcx.description(flat))
        assertFalse(StravaTcx.build(flat).contains(","))     // no locale's decimal comma
    }
}
