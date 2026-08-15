package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.Data
import com.seraphim.workbench.manifest.Platforms
import com.seraphim.workbench.manifest.Product
import com.seraphim.workbench.manifest.ProjectManifest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    private fun webTemplate(): Path {
        val template = directory.resolve("web-template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("include(\":shared:task-board\")\n__WEB_MODULES__")
        template.resolve("shared/local-data-web").createDirectories()
        template.resolve("shared/local-data-web/build.gradle.kts").writeText("plugins { id(\"web\") }")
        template.resolve("shared/local-data-sql").createDirectories()
        template.resolve("shared/local-data-sql/build.gradle.kts").writeText("plugins { id(\"sql\") }")
        template.resolve("shared/task-board").createDirectories()
        template.resolve("shared/task-board/build.gradle.kts").writeText("kotlin {\n__WASM_JS_TARGET__\n}")
        template.resolve("shared/task-board/src/wasmJsMain").createDirectories()
        template.resolve("shared/task-board/src/wasmJsMain/Web.kt").writeText("// web")
        template.resolve("shared/task-board/src/commonMain").createDirectories()
        template.resolve("shared/task-board/src/commonMain/Shared.kt").writeText("// shared")
        return template
    }

    @Test
    fun `web selection renders the web module and wasm target`() {
        val output = directory.resolve("web-board")
        ProductRenderer.render(RenderRequest(webManifest(), webTemplate(), output, "../../platform-kit"))
        assertTrue(output.resolve("shared/local-data-web/build.gradle.kts").isRegularFile())
        assertTrue(output.resolve("settings.gradle.kts").readText().contains("include(\":shared:local-data-web\")"))
        assertTrue(output.resolve("shared/task-board/build.gradle.kts").readText().contains("wasmJs { nodejs() }"))
        assertTrue(output.resolve("shared/task-board/src/wasmJsMain/Web.kt").isRegularFile())
    }

    @Test
    fun `without web the web module and wasm sources are absent`() {
        val output = directory.resolve("plain-board")
        ProductRenderer.render(RenderRequest(manifest(), webTemplate(), output, "../../platform-kit"))
        assertFalse(output.resolve("shared/local-data-web").exists())
        assertFalse(output.resolve("shared/task-board/src/wasmJsMain").exists())
        assertFalse(output.resolve("settings.gradle.kts").readText().contains("local-data-web"))
        assertFalse(output.resolve("shared/task-board/build.gradle.kts").readText().contains("wasmJs"))
        assertTrue(output.resolve("shared/local-data-sql/build.gradle.kts").isRegularFile())
        assertFalse(output.resolve("shared/task-board/build.gradle.kts").readText().contains("__"))
        // 空展开的 token 行应整行移除：settings 恰好一个结尾换行、无残留空行
        assertEquals(
            "include(\":shared:task-board\")",
            output.resolve("settings.gradle.kts").readText(),
        )
        assertEquals("kotlin {\n}", output.resolve("shared/task-board/build.gradle.kts").readText())
    }

    private fun webManifest() = ProjectManifest(
        schema = 1,
        product = Product("daily-board", "com.seraphim.dailyboard"),
        platforms = Platforms(true, true, false, true),
        data = Data("local-only"),
    )
}
