package com.seraphim.workbench.manifest

data class ProjectManifest(
    val schema: Int,
    val product: Product,
    val platforms: Platforms,
    val data: Data,
)

data class Product(val id: String, val packageName: String)
data class Platforms(val android: Boolean, val ios: Boolean, val desktop: Boolean, val web: Boolean)
data class Data(val strategy: String)
