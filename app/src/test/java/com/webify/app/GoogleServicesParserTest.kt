package com.webify.app

import com.webify.app.build.GoogleServicesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoogleServicesParserTest {

    private val sample = """
        {
          "project_info": {
            "project_number": "123456789012",
            "project_id": "toko-saya",
            "storage_bucket": "toko-saya.appspot.com"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:123456789012:android:other",
                "android_client_info": { "package_name": "com.other.app" }
              },
              "api_key": [ { "current_key": "KEY_OTHER" } ]
            },
            {
              "client_info": {
                "mobilesdk_app_id": "1:123456789012:android:abcdef",
                "android_client_info": { "package_name": "com.webify.tokosaya" }
              },
              "oauth_client": [ { "client_id": "web-client.apps.googleusercontent.com", "client_type": 3 } ],
              "api_key": [ { "current_key": "AIzaTEST" } ]
            }
          ],
          "configuration_version": "1"
        }
    """.trimIndent()

    @Test
    fun parsesMatchingClient() {
        val c = GoogleServicesParser.parse(sample, "com.webify.tokosaya")
        assertEquals("1:123456789012:android:abcdef", c.googleAppId)
        assertEquals("123456789012", c.gcmDefaultSenderId)
        assertEquals("AIzaTEST", c.apiKey)
        assertEquals("toko-saya", c.projectId)
        assertEquals("toko-saya.appspot.com", c.storageBucket)
        assertEquals("web-client.apps.googleusercontent.com", c.defaultWebClientId)
    }

    @Test
    fun rejectsUnknownPackage() {
        try {
            GoogleServicesParser.parse(sample, "com.nope.app")
            fail("harus melempar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("com.other.app"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidJson() {
        GoogleServicesParser.parse("bukan json", "com.webify.tokosaya")
    }

    @Test
    fun valuesXmlContainsRequiredStrings() {
        val xml = GoogleServicesParser.toValuesXml(GoogleServicesParser.parse(sample, "com.webify.tokosaya"))
        assertTrue(xml.contains("name=\"google_app_id\""))
        assertTrue(xml.contains("name=\"gcm_defaultSenderId\""))
        assertTrue(xml.contains("name=\"project_id\" translatable=\"false\">toko-saya<"))
    }
}
