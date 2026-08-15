package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.ManifestReader
import com.seraphim.workbench.manifest.ManifestValidator
import java.nio.file.Path
import kotlin.io.path.Path

fun main(args: Array<String>) {
    val values = args.toList().chunked(2).associate { it[0] to it[1] }
    val manifestPath = Path(values.getValue("--manifest")).toAbsolutePath()
    val templatePath = Path(values.getValue("--template")).toAbsolutePath()
    val outputPath = Path(values.getValue("--output")).toAbsolutePath()
    val platformKit = Path(values.getValue("--platform-kit")).toAbsolutePath().normalize()
    val platformKitPath = outputPath.relativize(platformKit).toString()
    val manifest = ManifestReader.read(manifestPath)
    ManifestValidator.requireSupported(manifest)
    ProductRenderer.render(RenderRequest(manifest, templatePath, outputPath, platformKitPath))
    println("Rendered ${manifest.product.id} at $outputPath")
}
