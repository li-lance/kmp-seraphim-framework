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
        val temporary = Files.createTempDirectory(request.output.parent, ".${request.output.name}-")
        try {
            val tokens = mapOf(
                "__PRODUCT_ID__" to request.manifest.product.id,
                "__PACKAGE_NAME__" to request.manifest.product.packageName,
                "__PACKAGE_PATH__" to request.manifest.product.packageName.replace('.', '/'),
                "__PLATFORM_KIT_PATH__" to request.platformKitPath,
            )
            Files.walk(request.template).use { paths ->
                paths.sorted().forEach { source ->
                    val relativeText = tokens.entries.fold(request.template.relativize(source).toString()) { value, token ->
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

    private fun requireDestination(output: Path) {
        if (!output.exists()) return
        check(output.isDirectory()) { "Output is not a directory: $output" }
        Files.list(output).use { check(it.findAny().isEmpty) { "Output directory is not empty: $output" } }
    }
}
