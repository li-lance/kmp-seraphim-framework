package com.seraphim.workbench.generator

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

object GeneratedTreeVerifier {
    fun verify(root: Path) {
        check(root.resolve("settings.gradle.kts").isRegularFile()) { "Missing settings.gradle.kts" }
        Files.walk(root).use { paths ->
            val unresolved = paths.filter { it.isRegularFile() && it.readText().contains("__") }.toList()
            check(unresolved.isEmpty()) { "Unresolved template tokens: ${unresolved.joinToString()}" }
        }
    }
}
