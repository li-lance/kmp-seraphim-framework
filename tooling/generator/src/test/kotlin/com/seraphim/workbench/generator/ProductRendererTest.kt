package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.Data
import com.seraphim.workbench.manifest.Platforms
import com.seraphim.workbench.manifest.Product
import com.seraphim.workbench.manifest.ProjectManifest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class ProductRendererTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `renders tokens and publishes the directory`() {
        val template = directory.resolve("template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("rootProject.name = \"__PRODUCT_ID__\"")
        template.resolve("project.yaml").writeText("package: __PACKAGE_NAME__\nkit: __PLATFORM_KIT_PATH__")
        val output = directory.resolve("daily-board")

        ProductRenderer.render(RenderRequest(manifest(), template, output, "../../platform-kit"))

        assertEquals("rootProject.name = \"daily-board\"", output.resolve("settings.gradle.kts").readText())
        assertFalse(output.resolve("project.yaml").readText().contains("__"))
    }

    @Test
    fun `does not modify a non-empty destination`() {
        val template = directory.resolve("template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("rootProject.name = \"__PRODUCT_ID__\"")
        val output = directory.resolve("daily-board").createDirectories()
        output.resolve("owned.txt").writeText("keep")

        assertFailsWith<IllegalStateException> {
            ProductRenderer.render(RenderRequest(manifest(), template, output, "../../platform-kit"))
        }

        assertEquals("keep", output.resolve("owned.txt").readText())
        assertFalse(output.resolve("project.yaml").exists())
    }

    private fun manifest() = ProjectManifest(
        schema = 1,
        product = Product("daily-board", "com.seraphim.dailyboard"),
        platforms = Platforms(true, true, false, false),
        data = Data("local-only"),
    )
}
