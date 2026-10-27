package com.casco.kettle.shell.nav

import org.junit.Assert.assertEquals
import org.junit.Test

private val rnRoutes = Tab.entries.associateWith<Tab, Screen> { Screen.Rn }

class TabNavigatorTest {
    private val sent = mutableListOf<String>()
    private val navigator = TabNavigator(rnRoutes, sent::add)

    @Test
    fun `a tap on an RN tab selects it and steers the surface there`() {
        navigator.select(Tab.History)

        assertEquals(Tab.History, navigator.current)
        assertEquals(listOf("/history"), sent)
    }

    @Test
    fun `a tap on a native tab selects it and leaves the surface where it is`() {
        val native = TabNavigator(rnRoutes + (Tab.History to Screen.Native {}), sent::add)

        native.select(Tab.History)

        assertEquals(Tab.History, native.current)
        assertEquals(emptyList<String>(), sent)
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
            TabNavigator(rnRoutes + (Tab.History to Screen.Native {}), sent::add, reread = { rereads++ })

        navigator.select(Tab.Library)
        navigator.select(Tab.History)

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
        val native = TabNavigator(rnRoutes + (Tab.Programs to Screen.Native {}), sent::add)

        native.select(Tab.History)
        native.select(Tab.Programs)
        native.onRoute("/history")

        assertEquals(Tab.Programs, native.current)
    }

    /**
     * Back from a native tab to the RN tab RN is still on: RN doesn't move, so it reports nothing, and
     * waiting for that report would leave every later move of RN's own ignored.
     */
    @Test
    fun `a tap back to the tab RN is already on waits for nothing`() {
        val native = TabNavigator(rnRoutes + (Tab.Programs to Screen.Native {}), sent::add)
        native.onRoute("/history")

        native.select(Tab.Programs)
        native.select(Tab.History)
        native.onRoute("/library")

        assertEquals(Tab.Library, native.current)
        assertEquals(emptyList<String>(), sent)
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
}
