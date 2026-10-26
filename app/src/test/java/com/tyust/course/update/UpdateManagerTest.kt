package com.tyust.course.update

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateManagerTest {
    private lateinit var app: Application
    private var manager: UpdateManager? = null
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("app_update_v2", 0).edit().clear().commit()
        File(app.filesDir, "app-updates").deleteRecursively()
    }
    @After fun cleanup() { manager?.close() }
    private fun create(server: MockWebServer): UpdateManager = UpdateManager(app, UpdateFixtures.keys,
        { listOf(server.url("/manifest").toString()) }, { it.startsWith(server.url("/").toString()) }).also { manager = it }
    private fun awaitCheck(manager: UpdateManager) = runBlocking {
        withTimeout(8000) { manager.state.first { it.check !in setOf(UpdateManager.Check.IDLE, UpdateManager.Check.CHECKING) } }
    }
    @Test fun invalidJsonIsFailureNotUpToDate() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBody("<html>error</html>")); val m = create(server); m.checkForUpdate(true)
        assertEquals(UpdateManager.Check.FAILED, awaitCheck(m).check)
    } }
    @Test fun successfulManifestOffersUpdate() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).put("versionName", "1.0.1000").toString())))
        val m = create(server); m.checkForUpdate(true); val result = awaitCheck(m)
        assertEquals(UpdateManager.Check.AVAILABLE, result.check); assertTrue(result.showDialog)
    } }
    @Test fun migratedClientCanCheckOlderStableReleaseWithoutDowngrade() { MockWebServer().use { server ->
        val m = create(server)
        assertFalse(m.state.value.testChannel)
        val stable = UpdateFixtures.payload().put("versionCode", m.getCurrentVersionCode() - 1).put("versionName", "1.0.97")
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(stable.toString())))
        m.checkForUpdate(true)
        assertEquals(UpdateManager.Check.UP_TO_DATE, awaitCheck(m).check)
        assertEquals(UpdateManager.Phase.IDLE, m.state.value.phase)
    } }
    @Test fun defaultChannelRejectsTestManifestInsteadOfSilentlyOptingIn() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(UpdateFixtures.payload().put("channel", "test").toString())))
        val m = create(server); m.checkForUpdate(true)
        assertEquals(UpdateManager.Check.FAILED, awaitCheck(m).check)
        assertFalse(m.state.value.testChannel)
    } }
    @Test fun concurrentChecksShareOneRequest() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBodyDelay(100, TimeUnit.MILLISECONDS).setBody(UpdateFixtures.envelope()))
        val m = create(server); repeat(20) { m.checkForUpdate(true) }; awaitCheck(m)
        assertEquals(1, server.requestCount)
    } }
    @Test fun closingPendingCheckDoesNotReopenWhenNewVersionArrives() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setBodyDelay(500, TimeUnit.MILLISECONDS).setBody(
            UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).put("versionName", "1.0.1000").toString())))
        val m = create(server); m.checkForUpdate(true)
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        m.dismiss()
        val result = awaitCheck(m)
        assertEquals(UpdateManager.Check.AVAILABLE, result.check)
        assertFalse(result.showDialog)
    } }
    @Test fun failedRefreshPreservesVerifiedCache() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }
        File(dir, "stable-manifest.json").writeText(UpdateFixtures.envelope())
        server.enqueue(MockResponse().setResponseCode(503)); val m = create(server); m.checkForUpdate(true)
        val result = awaitCheck(m); assertEquals(UpdateManager.Check.CACHED, result.check); assertNotNull(result.manifest)
    } }
    @Test fun tamperedCacheNotTrusted() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }; File(dir, "stable-manifest.json").writeText("{}")
        server.enqueue(MockResponse().setResponseCode(503)); val m = create(server); m.checkForUpdate(true)
        assertEquals(UpdateManager.Check.FAILED, awaitCheck(m).check)
    } }
    @Test fun sameVersionRepackIsNotAccepted() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }; File(dir, "stable-manifest.json").writeText(UpdateFixtures.envelope())
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(UpdateFixtures.payload().put("sha256", "c".repeat(64)).put("revision", 101).toString())))
        val m = create(server); m.checkForUpdate(true); val result = awaitCheck(m)
        assertEquals(UpdateManager.Check.CACHED, result.check); assertEquals("a".repeat(64), result.manifest!!.sha256)
    } }
    @Test fun onlyMatching304CanUseStoredEnvelope() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }; val envelope = UpdateFixtures.envelope()
        File(dir, "stable-manifest.json").writeText(envelope)
        app.getSharedPreferences("app_update_v2", 0).edit().putString("etag:${server.url("/manifest")}", "one")
            .putString("etag-payload:${server.url("/manifest")}", envelope).commit()
        server.enqueue(MockResponse().setResponseCode(304)); val m = create(server); m.checkForUpdate(true)
        assertNotEquals(UpdateManager.Check.CACHED, awaitCheck(m).check)
        assertEquals("one", server.takeRequest().getHeader("If-None-Match"))
    } }
    @Test fun automaticFailureDoesNotRemindFromCache() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }
        File(dir, "stable-manifest.json").writeText(UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).put("versionName", "1.0.1000").toString()))
        server.enqueue(MockResponse().setResponseCode(503)); val m = create(server); m.checkForUpdate()
        val state = awaitCheck(m); assertEquals(UpdateManager.Check.CACHED, state.check); assertFalse(state.showDialog)
    } }
    @Test fun verifiedTaskResumesAsPausedAndCancelDeletesPartial() { MockWebServer().use { server ->
        val dir = File(app.filesDir, "app-updates").apply { mkdirs() }
        val envelope = UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).put("versionName", "1.0.1000").toString())
        File(dir, "task.json").writeText(org.json.JSONObject().put("envelope", envelope).put("channel", "stable").toString())
        File(dir, "download.part").writeText("abc")
        val m = create(server)
        val state = runBlocking { withTimeout(5000) { m.state.first { it.phase == UpdateManager.Phase.PAUSED } } }
        assertEquals(3L, state.bytes); assertFalse(state.active)
        m.pauseDownload(cancel = true)
        runBlocking { withTimeout(5000) { m.state.first { it.phase == UpdateManager.Phase.CANCELLED } } }
        assertFalse(File(dir, "download.part").exists()); assertFalse(File(dir, "task.json").exists())
    } }
    @Test fun invalidArchiveNeverBecomesInstallable() { MockWebServer().use { server ->
        val file = File(app.cacheDir, "fake.apk").apply { writeText("bad") }
        val m = create(server)
        assertThrows(UpdateFailure::class.java) { m.verifyApk(UpdateFixtures.manifest(), file) }
    } }

    @Test fun failedAutomaticCheckCanRetryAfterOneMinute() { MockWebServer().use { server ->
        var now = 1_800_000_000_000L
        val m = UpdateManager(app, UpdateFixtures.keys, { listOf(server.url("/manifest").toString()) },
            { it.startsWith(server.url("/").toString()) }, { now }).also { manager = it }
        server.enqueue(MockResponse().setResponseCode(503))
        m.checkForUpdate()
        assertEquals(UpdateManager.Check.FAILED, awaitCheck(m).check)
        assertEquals(0L, app.getSharedPreferences("app_update_v2", 0).getLong("last-check:stable", 0))
        now += 61_000
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).toString())))
        m.checkForUpdate()
        runBlocking { withTimeout(8000) { m.state.first { it.check == UpdateManager.Check.AVAILABLE } } }
        assertEquals(2, server.requestCount)
    } }

    @Test fun oneFailedSourceDoesNotHideAnotherVerifiedSource() { MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope(UpdateFixtures.payload().put("versionCode", 1000).toString())))
        val m = UpdateManager(app, UpdateFixtures.keys,
            { listOf(server.url("/raw").toString(), server.url("/api").toString()) },
            { it.startsWith(server.url("/").toString()) }).also { manager = it }
        m.checkForUpdate(true)
        assertEquals(UpdateManager.Check.AVAILABLE, awaitCheck(m).check)
        assertEquals(2, server.requestCount)
    } }

    @Test fun manualCheckBypassesRecentSuccessfulAutomaticCheck() { MockWebServer().use { server ->
        val m = create(server)
        app.getSharedPreferences("app_update_v2", 0).edit().putLong("last-check:stable", System.currentTimeMillis()).commit()
        server.enqueue(MockResponse().setBody(UpdateFixtures.envelope()))
        m.checkForUpdate(true)
        assertNotEquals(UpdateManager.Check.FAILED, awaitCheck(m).check)
        assertEquals(1, server.requestCount)
    } }

}
