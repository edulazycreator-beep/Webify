package com.webify.app

import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.BuildStatus
import com.webify.app.domain.BuildType
import com.webify.app.domain.PlayStoreChecker
import com.webify.app.domain.PlayStoreChecker.Severity
import com.webify.app.ui.studio.assetLinksJson
import com.webify.app.ui.studio.diffSnapshots
import com.webify.app.ui.studio.searchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreCheckerTest {

    private val releaseKey = KeystoreEntity(id = "k1", name = "Rilis", filePath = "/x", keyAlias = "a")
    private val good = WebProjectEntity(
        appName = "Toko Saya", packageName = "com.tokosaya.app", targetUrl = "https://tokosaya.com",
        targetSdk = 36, versionCode = 5, selectedKeystoreId = "k1", privacyPolicyUrl = "https://tokosaya.com/privacy",
        iconUri = "/icon.png", enablePullToRefresh = true, deepLinkHost = "tokosaya.com"
    )

    private fun errors(p: WebProjectEntity, builds: List<BuildRecordEntity> = emptyList(), ks: KeystoreEntity? = releaseKey) =
        PlayStoreChecker.check(p, builds, ks, forBundle = true).filter { it.severity == Severity.ERROR }.map { it.title }

    @Test
    fun cleanProjectHasNoErrors() {
        assertEquals(emptyList<String>(), errors(good))
    }

    @Test
    fun detectsThirdPartyBrand() {
        val yt = good.copy(appName = "YouTube", packageName = "com.webify.youtube", targetUrl = "https://m.youtube.com")
        assertTrue(errors(yt).any { it.contains("YouTube") })
        // kata yang hanya mengandung substring merek tidak dianggap merek
        assertFalse(errors(good.copy(appName = "Grabber Tools")).any { it.contains("merek") })
    }

    @Test
    fun requiresTargetSdkSigningAndVersionBump() {
        assertTrue(errors(good.copy(targetSdk = 35)).any { it.contains("targetSdk") })
        assertTrue(errors(good, ks = null).any { it.contains("debug key") })
        val prev = BuildRecordEntity(projectId = good.id, buildType = BuildType.AAB_PLAYSTORE, status = BuildStatus.SUCCESS, versionCode = 5)
        assertTrue(errors(good, listOf(prev)).any { it.contains("versionCode") })
        assertFalse(errors(good.copy(versionCode = 6), listOf(prev)).any { it.contains("versionCode") })
    }

    @Test
    fun admobTestIdsAndPrivacy() {
        val ads = good.copy(admobEnabled = true, admobAppId = "ca-app-pub-3940256099942544~3347511713", privacyPolicyUrl = "")
        val e = errors(ads)
        assertTrue(e.any { it.contains("uji") })
        assertTrue(e.any { it.contains("privasi") })
    }

    @Test
    fun snapshotDiff() {
        val a = """{"appName":"A","primaryColorHex":"#111111","updatedAt":1}"""
        val b = """{"appName":"A","primaryColorHex":"#222222","updatedAt":2}"""
        val d = diffSnapshots(a, b)
        assertEquals(1, d.size)
        assertEquals("primary color hex", d[0].field)
        assertEquals("#111111", d[0].before)
    }

    @Test
    fun assetLinksAndSearch() {
        val json = assetLinksJson("com.x.y", listOf("aa:bb", "", "aa:bb"))
        assertTrue(json.contains("\"com.x.y\""))
        assertEquals(1, Regex("AA:BB").findAll(json).count())
        assertTrue(searchSettings("admob").any { it.tab == 4 })
        assertTrue(searchSettings("sidik jari").any { it.label.contains("biometrik") })
        assertTrue(searchSettings("").isEmpty())
    }
}
