package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.ProjectManifest
import java.nio.file.Path

data class RenderRequest(
    val manifest: ProjectManifest,
    val template: Path,
    val output: Path,
    val platformKitPath: String,
)
