package com.seraphim.workbench.manifest

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class ManifestTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `reads the supported Phase 0 manifest`() {
        val file = directory.resolve("project.yaml")
        file.writeText(SUPPORTED)

        val manifest = ManifestReader.read(file)

        ManifestValidator.requireSupported(manifest)
        assertEquals("daily-board", manifest.product.id)
    }

    @Test
    fun `rejects a Web target before writing`() {
        val file = directory.resolve("project.yaml")
        file.writeText(SUPPORTED.replace("web: false", "web: true"))

        val error = assertFailsWith<IllegalArgumentException> {
            ManifestValidator.requireSupported(ManifestReader.read(file))
        }

        assertEquals("Phase 0 supports exactly Android+iOS with local-only data", error.message)
    }

    private companion object {
        val SUPPORTED = """
            schema: 1
            product:
              id: daily-board
              package: com.seraphim.dailyboard
            platforms:
              android: true
              ios: true
              desktop: false
              web: false
            data:
              strategy: local-only
        """.trimIndent()
    }
}
