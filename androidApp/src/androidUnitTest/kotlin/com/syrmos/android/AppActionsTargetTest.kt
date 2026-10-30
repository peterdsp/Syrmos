package com.syrmos.android

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guardrail for the Google Assistant App Actions declared in `res/xml/actions.xml`.
 *
 * Every capability fires an explicit intent at a target package and class. Those
 * are plain strings, so nothing in the build fails when they do not match the
 * real applicationId: the capability simply never resolves and "Hey Google, next
 * train from Syntagma on Syrmos" does nothing. They were pointed at
 * `com.syrmos.app` while the app ships as `com.syrmos.android`, which is exactly
 * that silent failure, so pin them here.
 */
class AppActionsTargetTest {

    private val applicationId = "com.syrmos.android"

    private val actionsXml: String by lazy {
        // Unit tests run with the module directory as the working directory.
        val f = File("src/androidMain/res/xml/actions.xml")
        assertTrue(f.exists(), "actions.xml not found at ${f.absolutePath}")
        f.readText()
    }

    private fun attributeValues(name: String): List<String> =
        Regex("""android:$name="([^"]+)"""").findAll(actionsXml).map { it.groupValues[1] }.toList()

    @Test fun every_capability_targets_the_real_application_id() {
        val packages = attributeValues("targetPackage")
        assertTrue(packages.isNotEmpty(), "no targetPackage declared")
        packages.forEach { assertEquals(applicationId, it) }
    }

    @Test fun every_capability_targets_an_activity_in_the_app_namespace() {
        val classes = attributeValues("targetClass")
        assertEquals(attributeValues("targetPackage").size, classes.size)
        classes.forEach { assertEquals("$applicationId.MainActivity", it) }
    }

    @Test fun each_declared_capability_passes_a_parameter_main_activity_reads() {
        // MainActivity.handleAssistantIntent only understands these three keys;
        // a capability passing a different key is a dead voice command. Read the
        // keys off <parameter> elements only, not off <capability-binding>.
        val keys = Regex("""<parameter\b[^>]*android:key="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
            .findAll(actionsXml).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("station", "line", "question"), keys)
    }
}
