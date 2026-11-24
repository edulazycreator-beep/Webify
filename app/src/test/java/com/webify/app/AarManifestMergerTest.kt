package com.webify.app

import com.webify.app.build.AarManifestMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AarManifestMergerTest {

    private val main = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.webify.demo">
          <uses-permission android:name="android.permission.INTERNET"/>
          <application android:label="Demo">
            <activity android:name=".MainActivity" android:exported="true"/>
          </application>
        </manifest>
    """.trimIndent()

    private val lib = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:tools="http://schemas.android.com/tools" package="com.lib">
          <uses-permission android:name="android.permission.INTERNET"/>
          <uses-permission android:name="android.permission.WAKE_LOCK"/>
          <uses-permission android:name="android.permission.READ_PHONE_STATE" tools:node="remove"/>
          <queries><intent><action android:name="android.intent.action.VIEW"/></intent></queries>
          <application>
            <provider android:name="com.lib.InitProvider" android:authorities="${'$'}{applicationId}.libinit" tools:replace="android:authorities"/>
            <activity android:name=".MainActivity"/>
            <meta-data android:name="com.lib.VERSION" android:value="1"/>
          </application>
        </manifest>
    """.trimIndent()

    @Test
    fun mergesWithoutDuplicates() {
        val r = AarManifestMerger.merge(main, listOf("lib" to lib), "com.webify.demo")
        assertEquals(1, Regex("android.permission.INTERNET").findAll(r.xml).count())
        assertTrue(r.xml.contains("android.permission.WAKE_LOCK"))
        assertFalse(r.xml.contains("READ_PHONE_STATE"))
        assertTrue(r.xml.contains("com.webify.demo.libinit"))
        assertTrue(r.xml.contains("com.lib.VERSION"))
        assertTrue(r.xml.contains("<queries"))
        assertEquals(1, Regex("\\.MainActivity").findAll(r.xml).count())
        assertFalse("atribut tools harus dibuang", r.xml.contains("tools:replace"))
        assertEquals(listOf("android.permission.WAKE_LOCK"), r.permissions)
    }

    @Test
    fun mergingTwiceIsIdempotent() {
        val once = AarManifestMerger.merge(main, listOf("lib" to lib), "com.webify.demo")
        val twice = AarManifestMerger.merge(once.xml, listOf("lib" to lib), "com.webify.demo")
        assertEquals(0, twice.added)
    }
}
