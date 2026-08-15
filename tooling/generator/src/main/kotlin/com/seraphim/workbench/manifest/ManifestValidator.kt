package com.seraphim.workbench.manifest

object ManifestValidator {
    private val productId = Regex("[a-z][a-z0-9-]*")
    private val packageName = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")

    fun requireSupported(manifest: ProjectManifest) {
        require(manifest.schema == 1) { "Unsupported manifest schema: ${manifest.schema}" }
        require(productId.matches(manifest.product.id)) { "Invalid product id" }
        require(packageName.matches(manifest.product.packageName)) { "Invalid package name" }
        require(
            manifest.platforms.android && manifest.platforms.ios &&
                !manifest.platforms.desktop && !manifest.platforms.web &&
                manifest.data.strategy == "local-only"
        ) { "Phase 0 supports exactly Android+iOS with local-only data" }
    }
}
