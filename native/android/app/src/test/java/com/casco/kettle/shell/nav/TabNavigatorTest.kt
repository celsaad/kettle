package com.casco.kettle.shell.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every route RN's: a path nothing registers renders RN. */
private val rnRoutes = emptyMap<String, Screen>()

class TabNavigatorTest {
    private val sent = mutableListOf<String>()
    private val navigator = TabNavigator(rnRoutes, sent::add)

    /** The runner is dark in either scheme, so the system bars' icons go light over it (#248). */
    @Test
    fun `the runner shows while RN reports it, and not once RN is back on a tab`() {
        assertEquals(false, navigator.runnerShown)

        navigator.onRoute("/session")

        assertEquals(true, navigator.runnerShown)

        navigator.onRoute("/history")

        assertEquals(false, navigator.runnerShown)
    }

    @Test
    fun `a tap on an RN tab selects it and steers the surface there`() {
        navigator.select(Tab.History)

        assertEquals(Tab.History, navigator.current)
        assertEquals(listOf("/history"), sent)
    }

    /** A native tab still has its stub route in RN, and RN has to be on it (#190). */
    @Test
    fun `a tap on a native tab selects it and steers the surface to its stub`() {
        val native = TabNavigator(rnRoutes + ("/history" to Screen.Native {}), sent::add)

        native.select(Tab.History)

        assertEquals(Tab.History, native.current)
        assertEquals(listOf("/history"), sent)
        assertEquals(Layer.NativeTab, native.layer)
    }

    /**
     * What steering to the stub is for: a sheet a native tab opens is pushed over the stub, so closing it
     * reports that tab. Left on the tab it was on, RN reported Library on the close, and the bar moved.
     */
    @Test
    fun `a sheet opened from a native tab closes back onto that tab`() {
        val native = TabNavigator(rnRoutes + ("/history" to Screen.Native {}), sent::add)
        native.onRoute("/library")
        native.select(Tab.History)
        native.onRoute("/history")

        native.onRoute("/session-editor")
        native.onRoute("/history")

        assertEquals(Tab.History, native.current)
    }

    /** A native screen behind an RN stub reads `id` or `exerciseId` from its own route's report. */
    @Test
    fun `each path keeps the params it was last reported with`() {
        navigator.onRoute("/history", mapOf("from" to "runner"))
        navigator.onRoute("/exercise-progress", mapOf("exerciseId" to "kb-swing"))

        assertEquals(mapOf("from" to "runner"), navigator.params("/history"))
        assertEquals(mapOf("exerciseId" to "kb-swing"), navigator.params("/exercise-progress"))
        assertEquals(emptyMap<String, String>(), navigator.params("/settings"))

        navigator.onRoute("/exercise-progress", mapOf("exerciseId" to "goblet-squat"))

        assertEquals(mapOf("exerciseId" to "goblet-squat"), navigator.params("/exercise-progress"))
    }

    /** RN draws its own modals inside the surface, so one over a native tab needs the surface. */
    @Test
    fun `an RN sheet over a native tab shows the surface`() {
        val native = TabNavigator(rnRoutes + ("/history" to Screen.Native {}), sent::add)
        native.select(Tab.History)
        native.onRoute("/history")

        native.onRoute("/session-editor")

        assertEquals(Layer.Surface, native.layer)
        assertEquals(null, native.nativeSheetPath)
    }

    /** RN shows only the stub at a native route above the tabs, so native draws it in the sheet (#197). */
    @Test
    fun `a native route above the tabs is the native sheet's, and hands back to RN's`() {
        val native = TabNavigator(rnRoutes + ("/support" to Screen.Native {}), sent::add)

        native.onRoute("/settings")
        native.onRoute("/support")

        assertEquals(Layer.NativeSheet, native.layer)
        assertEquals("/support", native.nativeSheetPath)

        native.onRoute("/settings")

        assertEquals(Layer.Surface, native.layer)
        assertEquals(null, native.nativeSheetPath)

        native.onRoute("/")

        assertEquals(Layer.Surface, native.layer)
    }

    /** RN moves itself too: a delete's `dismissTo`, the runner's exit to History. */
    @Test
    fun `RN's report of a tab selects it, without steering RN back there`() {
        navigator.onRoute("/history")

        assertEquals(Tab.History, navigator.current)
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `the bar hides while something above the tabs shows, and comes back with the tab under it`() {
        navigator.onRoute("/programs")
        navigator.onRoute("/program-detail")

        assertEquals(false, navigator.barShown)
        assertEquals(Tab.Programs, navigator.current)

        navigator.onRoute("/programs")

        assertEquals(true, navigator.barShown)
    }

    /** What RN showed above the tabs may have written a file a native screen reads; a tab can't. */
    @Test
    fun `only the report that brings the bar back is a hand-back`() {
        var handBacks = 0
        val navigator = TabNavigator(rnRoutes, sent::add, reread = { handBacks++ })

        navigator.onRoute("/history")
        navigator.onRoute("/session")
        navigator.onRoute("/session-summary")
        navigator.onRoute("/history")
        navigator.onRoute("/library")

        assertEquals(1, handBacks)
    }

    /**
     * What RN writes while a tab shows (a first launch's seeded library, RN History's delete) has no
     * hand-back to follow, so a native screen reads again as it comes into view.
     */
    @Test
    fun `a tap onto a native tab rereads, and a tap onto an RN one doesn't`() {
        var rereads = 0
        val navigator =
            TabNavigator(rnRoutes + ("/history" to Screen.Native {}), sent::add, reread = { rereads++ })

        navigator.select(Tab.Library)
        navigator.select(Tab.History)

        assertEquals(1, rereads)
    }

    /**
     * The same, for a native sheet over an RN tab: RN History's delete, then RN Stats' row opening
     * exercise progress, never hands the tabs back in between. Once per sheet, not per report: the
     * report a sheet's params changed in is the same screen.
     */
    @Test
    fun `a native sheet coming up rereads, and an RN modal doesn't`() {
        var rereads = 0
        val navigator =
            TabNavigator(rnRoutes + ("/exercise-progress" to Screen.Native {}), sent::add, reread = { rereads++ })
        navigator.onRoute("/history")

        navigator.onRoute("/analytics")
        navigator.onRoute("/exercise-progress", mapOf("exerciseId" to "rdl"))
        navigator.onRoute("/exercise-progress", mapOf("exerciseId" to "rdl"))

        assertEquals(1, rereads)
    }

    /** The report of the first tap arrives after the second: following it would flick back a tab. */
    @Test
    fun `a report from before the latest tap is not followed`() {
        navigator.select(Tab.Library)
        navigator.select(Tab.History)
        navigator.onRoute("/library")

        assertEquals(Tab.History, navigator.current)

        navigator.onRoute("/history")
        navigator.onRoute("/programs")

        assertEquals(Tab.Programs, navigator.current)
    }

    /** As the app has always done: back from any tab is Workouts first, and only then out of the app. */
    @Test
    fun `back goes to Workouts first, then lets the app leave`() {
        navigator.select(Tab.Programs)

        assertEquals(true, navigator.back())
        assertEquals(Tab.Workouts, navigator.current)
        assertEquals(listOf("/programs", "/"), sent)

        assertEquals(false, navigator.back())
        assertEquals(listOf("/programs", "/"), sent)
    }

    /** RN's answer to the first tap arrives after the second, which went to a tab RN isn't drawing. */
    @Test
    fun `RN's answer to an earlier tap doesn't pull the bar off a native tab`() {
        val native = TabNavigator(rnRoutes + ("/programs" to Screen.Native {}), sent::add)

        native.select(Tab.History)
        native.select(Tab.Programs)
        native.onRoute("/history")

        assertEquals(Tab.Programs, native.current)
    }

    /**
     * Back from a native tab to the RN tab RN was on: RN has gone to the stub since, so it has to be
     * sent back, and once it has answered, its own moves are followed again.
     */
    @Test
    fun `a tap back from a native tab steers RN off the stub`() {
        val native = TabNavigator(rnRoutes + ("/programs" to Screen.Native {}), sent::add)
        native.onRoute("/history")

        native.select(Tab.Programs)
        native.onRoute("/programs")
        native.select(Tab.History)
        native.onRoute("/history")
        native.onRoute("/library")

        assertEquals(Tab.Library, native.current)
        assertEquals(listOf("/programs", "/history"), sent)
    }

    /**
     * A tap back to where RN last was, before RN has answered the tap away: RN is on its way somewhere
     * else, so it has to be sent back. Sending nothing left the bar on Workouts over History, and back
     * then left the app.
     */
    @Test
    fun `a quick tap back steers RN back too`() {
        navigator.onRoute("/")

        navigator.select(Tab.History)
        navigator.select(Tab.Workouts)
        navigator.onRoute("/history")

        assertEquals(listOf("/history", "/"), sent)
        assertEquals(Tab.Workouts, navigator.current)

        navigator.onRoute("/")
        navigator.onRoute("/library")

        assertEquals(Tab.Library, navigator.current)
    }

    @Test
    fun `a tap on the tab already showing sends nothing`() {
        navigator.select(Tab.Workouts)

        assertEquals(emptyList<String>(), sent)
    }

    /** #153 dropped re-tap-to-top; each tab's port restores it for its own screen, from this. */
    @Test
    fun `a tap on the tab already showing is told to that tab, and only a tap on it`() {
        val heard = mutableListOf<Tab>()
        val navigator = TabNavigator(rnRoutes, sent::add, onReselect = heard::add)

        navigator.select(Tab.History)
        navigator.select(Tab.History)
        navigator.onRoute("/history")
        navigator.select(Tab.Library)

        assertEquals(listOf(Tab.History), heard)
    }
}
