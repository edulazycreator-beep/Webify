package com.webify.app

import com.webify.app.build.MavenCoordinate
import com.webify.app.build.MavenVersion
import com.webify.app.build.PomParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenTest {

    @Test
    fun coordinateParsing() {
        assertEquals(MavenCoordinate("com.google.zxing", "core", "3.5.3"), MavenCoordinate.parse(" com.google.zxing:core:3.5.3 "))
        assertEquals("1.0", MavenCoordinate.parse("a:b:1.0@aar")!!.version)
        assertNull(MavenCoordinate.parse("a:b"))
        assertNull(MavenCoordinate.parse("a::1.0"))
    }

    @Test
    fun versionNormalize() {
        assertEquals("1.2.3", MavenVersion.normalize("[1.2.3]"))
        assertEquals("1.0", MavenVersion.normalize("[1.0,2.0)"))
        assertEquals("2.1", MavenVersion.normalize("2.1"))
    }

    @Test
    fun versionCompare() {
        assertTrue(MavenVersion.compare("1.10.0", "1.9.9") > 0)
        assertTrue(MavenVersion.compare("2.0.0-rc01", "2.0.0") < 0)
        assertTrue(MavenVersion.compare("2.0.0-alpha01", "2.0.0-beta01") < 0)
        assertTrue(MavenVersion.compare("23.4.1", "23.4") > 0)
        assertEquals(0, MavenVersion.compare("1.0.0", "1.0.0"))
    }

    @Test
    fun pomResolvesPropertiesParentAndScopes() {
        val parent = """
            <project>
              <groupId>com.example</groupId><artifactId>parent</artifactId><version>5</version>
              <properties><okio.version>3.6.0</okio.version></properties>
              <dependencyManagement><dependencies>
                <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>1.9.10</version></dependency>
              </dependencies></dependencyManagement>
            </project>
        """.trimIndent()
        val pom = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <parent><groupId>com.example</groupId><artifactId>parent</artifactId><version>5</version></parent>
              <artifactId>lib</artifactId>
              <packaging>aar</packaging>
              <dependencies>
                <dependency><groupId>com.squareup.okio</groupId><artifactId>okio</artifactId><version>${'$'}{okio.version}</version></dependency>
                <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId></dependency>
                <dependency><groupId>${'$'}{project.groupId}</groupId><artifactId>sibling</artifactId><version>${'$'}{project.version}</version><scope>runtime</scope></dependency>
                <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>
                <dependency><groupId>x</groupId><artifactId>opt</artifactId><version>1</version><optional>true</optional></dependency>
                <dependency><groupId>x</groupId><artifactId>missing</artifactId></dependency>
              </dependencies>
            </project>
        """.trimIndent()
        val self = MavenCoordinate("com.example", "lib", "1.2.0")
        val info = PomParser.parse(pom, self) { c -> if (c.artifact == "parent") parent else null }
        assertEquals("aar", info.packaging)
        assertEquals(
            listOf(
                MavenCoordinate("com.squareup.okio", "okio", "3.6.0"),
                MavenCoordinate("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.10"),
                MavenCoordinate("com.example", "sibling", "1.2.0")
            ),
            info.dependencies
        )
        assertEquals(listOf("x:missing"), info.unresolved)
    }
}
