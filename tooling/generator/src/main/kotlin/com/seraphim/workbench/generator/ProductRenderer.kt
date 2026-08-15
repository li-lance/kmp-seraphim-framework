package com.seraphim.workbench.generator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ProductRenderer {
    fun render(request: RenderRequest): Path {
        requireDestination(request.output)
        request.output.parent.createDirectories()
        val temporary = Files.createTempDirectory(request.output.parent, "." + request.output.name + "-")
        try {
            val webSelected = request.manifest.platforms.web
            val tokens = mapOf(
                "__PRODUCT_ID__" to request.manifest.product.id,
                "__PACKAGE_NAME__" to request.manifest.product.packageName,
                "__PACKAGE_PATH__" to request.manifest.product.packageName.replace('.', '/'),
                "__PLATFORM_KIT_PATH__" to request.platformKitPath,
                "__WEB_MODULES__" to if (webSelected) "include(\":shared:local-data-web\")" else "",
                "__WASM_JS_TARGET__" to if (webSelected) "wasmJs { nodejs() }" else "",
            )
            Files.walk(request.template).use { paths ->
                paths.sorted().forEach { source ->
                    val relative = request.template.relativize(source)
                    if (!webSelected && isWebOnly(relative)) return@forEach
                    val relativeText = tokens.entries.fold(relative.toString()) { value, token ->
                        value.replace(token.key, token.value)
                    }
                    val target = temporary.resolve(relativeText)
                    when {
                        source.isDirectory() -> target.createDirectories()
                        source.isRegularFile() -> {
                            target.parent.createDirectories()
                            val rendered = tokens.entries.fold(source.readText()) { value, token ->
                                value.replace(token.key, token.value)
                            }
                            target.writeText(rendered)
                        }
                    }
                }
            }
            GeneratedTreeVerifier.verify(temporary)
            if (request.output.exists()) Files.delete(request.output)
            Files.move(temporary, request.output, StandardCopyOption.ATOMIC_MOVE)
            return request.output
        } catch (failure: Throwable) {
            temporary.toFile().deleteRecursively()
            throw failure
        }
    }

    private fun isWebOnly(relative: Path): Boolean {
        val segments = relative.map { it.toString() }
        return segments.contains("wasmJsMain") || segments.contains("wasmJsTest") ||
            (segments.size >= 2 && segments[0] == "shared" && segments[1] == "local-data-web")
    }

    private fun requireDestination(output: Path) {
        if (!output.exists()) return
        check(output.isDirectory()) { "Output is not a directory: $output" }
        Files.list(output).use { check(it.findAny().isEmpty) { "Output directory is not empty: $output" } }
    }
}
