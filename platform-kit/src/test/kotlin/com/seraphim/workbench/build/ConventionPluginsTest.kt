package com.seraphim.workbench.build

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConventionPluginsTest {
    @Test
    fun `kotlin policy does not select Android`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinPolicyPlugin::class.java)

        assertFalse(project.pluginManager.hasPlugin("com.android.kotlin.multiplatform.library"))
    }

    @Test
    fun `toolchain values match compatibility lock`() {
        assertEquals(17, Toolchain.JAVA)
        assertEquals(37, Toolchain.COMPILE_SDK)
        assertEquals(37, Toolchain.TARGET_SDK)
        assertEquals(26, Toolchain.MIN_SDK)
    }

    @Test
    fun `Android application policy does not apply the application plugin`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(AndroidApplicationPlugin::class.java)

        assertFalse(project.pluginManager.hasPlugin("com.android.application"))
    }
}
