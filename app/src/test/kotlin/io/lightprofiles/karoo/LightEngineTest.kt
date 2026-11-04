package io.lightprofiles.karoo

import io.lightprofiles.karoo.data.AppSettings
import io.lightprofiles.karoo.data.AutoActivate
import io.lightprofiles.karoo.data.Band
import io.lightprofiles.karoo.data.BandSource
import io.lightprofiles.karoo.data.LightRole
import io.lightprofiles.karoo.data.LightSetup
import io.lightprofiles.karoo.data.ModeResolver
import io.lightprofiles.karoo.data.Profile
import io.lightprofiles.karoo.data.RadarBehaviour
import io.lightprofiles.karoo.engine.BandController
import io.lightprofiles.karoo.engine.LightEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three rides this app exists for, written as tests so a change that breaks
 * one of them fails here rather than on a dark forest trail.
 */
class LightEngineTest {

    private val front = "front-1"
    private val rear = "rear-1"

    private var clock = 1_000_000L

    private fun engineFor(settings: AppSettings): Pair<LightEngine, MutableMap<String, String>> {
        val engine = LightEngine(BandController { clock }) { clock }
        val applied = mutableMapOf<String, String>()
        engine.onApply = { out ->
            out.profile?.lights?.forEach { setup ->
                applied[setup.deviceId] = ModeResolver.resolve(
                    setup = setup,
                    band = out.band,
                    radarActive = out.radarActive,
                    lightsOn = out.lightsOn,
                )
            }
        }
        engine.settings = settings
        return engine to applied
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    // ---- Use case 1: daylight on the road ----

    private fun dayRoadProfile() = Profile(
        id = "day",
        name = "Day road",
        autoActivate = AutoActivate.DAY,
        lights = listOf(
            LightSetup(front, "Front", LightRole.FRONT, mode = "OFF"),
            LightSetup(
                rear,
                "Rear",
                LightRole.REAR,
                mode = "OFF",
                radar = RadarBehaviour.ONLY_ON_VEHICLE,
                radarMode = "FAST_FLASH",
            ),
        ),
    )

    @Test
    fun `day road - rear light only burns when a vehicle is behind`() {
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(dayRoadProfile()), activeProfileId = "day", radarHoldSeconds = 1),
        )

        engine.onRideStart()
        assertEquals("OFF", applied[front])
        assertEquals("OFF", applied[rear])

        engine.onRadarThreat(true)
        assertEquals("FAST_FLASH", applied[rear])
        assertEquals("OFF", applied[front])

        engine.onRadarThreat(false)
        waitUntil { applied[rear] == "OFF" }
    }

    @Test
    fun `day road - a flashing front light is just another normal mode`() {
        val profile = dayRoadProfile().let {
            it.copy(lights = it.lights.map { l -> if (l.deviceId == front) l.copy(mode = "SLOW_FLASH") else l })
        }
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(profile), activeProfileId = "day"),
        )

        engine.onRideStart()

        assertEquals("SLOW_FLASH", applied[front])
    }

    // ---- Use case 2: night in the woods, brightness follows the gradient ----

    private fun nightWoodsProfile() = Profile(
        id = "woods",
        name = "Woods night",
        autoActivate = AutoActivate.NIGHT,
        bandSource = BandSource.GRADE,
        gradeUphillPct = 3,
        gradeDownhillPct = -3,
        lights = listOf(
            LightSetup(
                front,
                "Front",
                LightRole.FRONT,
                // STEADY1 is Solid Max and STEADY5 Solid Min: the levels count
                // down from brightest, so dim on the climb is STEADY5.
                mode = "STEADY3",
                bandsEnabled = true,
                slowMode = "STEADY5",
                fastMode = "STEADY1",
            ),
            LightSetup(rear, "Rear", LightRole.REAR, mode = "SLOW_FLASH"),
        ),
    )

    @Test
    fun `woods at night - uphill dims, downhill brightens, rear keeps flashing`() {
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(nightWoodsProfile()), activeProfileId = "woods"),
        )

        engine.onRideStart()
        assertEquals("STEADY3", applied[front])
        assertEquals("SLOW_FLASH", applied[rear])

        // 6% climb: dim.
        engine.onSample(6.0)
        waitUntil { applied[front] == "STEADY5" }
        assertEquals("SLOW_FLASH", applied[rear])

        // Over the top and down the other side: full beam.
        clock += 20_000
        engine.onSample(-6.0)
        waitUntil { applied[front] == "STEADY1" }

        // Flat again.
        clock += 20_000
        engine.onSample(0.0)
        waitUntil { applied[front] == "STEADY3" }
    }

    // ---- Use case 3: riding through town to get there ----

    @Test
    fun `town transfer - same lights, bands off, nothing changes with the road`() {
        val profile = nightWoodsProfile().let {
            it.copy(
                id = "town",
                name = "Town",
                lights = it.lights.map { l -> l.copy(bandsEnabled = false) },
            )
        }
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(profile), activeProfileId = "town"),
        )

        engine.onRideStart()
        engine.onSample(-8.0)
        Thread.sleep(200)

        assertEquals("STEADY3", applied[front])
    }

    // ---- Switching ----

    @Test
    fun `the handlebar button steps through the profiles and wraps`() {
        val settings = AppSettings(
            profiles = listOf(dayRoadProfile(), nightWoodsProfile()),
            activeProfileId = "day",
        )
        val (engine, applied) = engineFor(settings)
        engine.onRideStart()
        assertEquals("OFF", applied[front])

        val second = engine.nextProfile()
        assertEquals("Woods night", second?.name)
        assertEquals("STEADY3", applied[front])

        val third = engine.nextProfile()
        assertEquals("Day road", third?.name)
        assertEquals("OFF", applied[front])
    }

    @Test
    fun `the sun picks the night profile, and a manual choice then wins`() {
        val (engine, applied) = engineFor(
            AppSettings(
                profiles = listOf(dayRoadProfile(), nightWoodsProfile()),
                activeProfileId = "day",
                autoSwitchBySun = true,
            ),
        )
        // Middle of the night over Berlin.
        clock = java.time.Instant.parse("2026-11-04T01:00:00Z").toEpochMilli()
        engine.onLocation(52.52, 13.405)

        engine.onRideStart()

        assertEquals("woods", engine.activeProfile?.id)
        assertEquals("STEADY3", applied[front])

        engine.selectProfile("day")
        assertEquals("day", engine.activeProfile?.id)
        assertEquals("OFF", applied[front])
    }

    // ---- The rule that must never break ----

    @Test
    fun `no ride means no light, whatever the radar says`() {
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(dayRoadProfile()), activeProfileId = "day"),
        )

        engine.onRadarThreat(true)

        assertEquals("OFF", applied[rear])
    }

    @Test
    fun `ride stop switches everything off`() {
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(nightWoodsProfile()), activeProfileId = "woods"),
        )
        engine.onRideStart()
        assertEquals("STEADY3", applied[front])

        engine.onRideStop()

        assertEquals("OFF", applied[front])
        assertEquals("OFF", applied[rear])
    }

    @Test
    fun `auto-pause changes nothing`() {
        val (engine, applied) = engineFor(
            AppSettings(profiles = listOf(nightWoodsProfile()), activeProfileId = "woods"),
        )
        engine.onRideStart()

        engine.onRidePause()

        assertEquals("STEADY3", applied[front])
        assertNotNull(engine.activeProfile)
    }

    @Test
    fun `bands hold a change for the configured time`() {
        val (engine, applied) = engineFor(
            AppSettings(
                profiles = listOf(nightWoodsProfile()),
                activeProfileId = "woods",
                bandHoldSeconds = 15,
            ),
        )
        engine.onRideStart()

        engine.onSample(6.0)
        waitUntil { applied[front] == "STEADY5" }

        // Straight back to flat: too soon, so the band stays put.
        engine.onSample(0.0)
        Thread.sleep(200)
        assertEquals("STEADY5", applied[front])

        clock += 16_000
        engine.onSample(0.0)
        waitUntil { applied[front] == "STEADY3" }
    }

    @Test
    fun `band edges sit where the profile puts them`() {
        val controller = BandController { clock }
        controller.configure(nightWoodsProfile().copy(bandSource = BandSource.SPEED))
        controller.hysteresis = 3.0

        controller.onSample(8.0)
        assertEquals(Band.SLOW, controller.band.value)

        clock += 20_000
        controller.onSample(20.0)
        assertEquals(Band.NORMAL, controller.band.value)

        clock += 20_000
        controller.onSample(35.0)
        assertEquals(Band.FAST, controller.band.value)

        // Inside the hysteresis gap below the fast edge: stays fast.
        clock += 20_000
        controller.onSample(26.0)
        assertEquals(Band.FAST, controller.band.value)

        controller.onSample(24.0)
        assertEquals(Band.NORMAL, controller.band.value)
    }
}
