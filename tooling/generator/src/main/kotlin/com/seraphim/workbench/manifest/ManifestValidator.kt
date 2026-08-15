package com.seraphim.workbench.manifest

object ManifestValidator {
    private val productId = Regex("[a-z][a-z0-9-]*")
    private val packageName = Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")

    fun requireSupported(manifest: ProjectManifest) {
        require(manifest.schema == 1) { "Unsupported manifest schema: " + manifest.schema }
        require(productId.matches(manifest.product.id)) { "Invalid product id" }
        require(packageName.matches(manifest.product.packageName)) { "Invalid package name" }
        require(manifest.data.strategy == "local-only") {
            "Unsupported data strategy: " + manifest.data.strategy
        }
        val platforms = manifest.platforms
        val supported = platforms.android && platforms.ios && !platforms.desktop
        require(supported) {
            "Supported platforms are Android+iOS, optionally with Web; desktop is not yet supported"
        }
    }
}
