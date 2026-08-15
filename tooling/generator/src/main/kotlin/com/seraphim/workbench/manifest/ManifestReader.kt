package com.seraphim.workbench.manifest

import java.nio.file.Path
import kotlin.io.path.reader
import org.yaml.snakeyaml.Yaml

object ManifestReader {
    fun read(path: Path): ProjectManifest {
        val root = path.reader().use { Yaml().load<Map<String, Any?>>(it) }
        val product = root.map("product")
        val platforms = root.map("platforms")
        val data = root.map("data")
        return ProjectManifest(
            schema = root.int("schema"),
            product = Product(product.string("id"), product.string("package")),
            platforms = Platforms(
                android = platforms.boolean("android"),
                ios = platforms.boolean("ios"),
                desktop = platforms.boolean("desktop"),
                web = platforms.boolean("web"),
            ),
            data = Data(data.string("strategy")),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.map(key: String) = get(key) as? Map<String, Any?>
        ?: error("Missing object: $key")
    private fun Map<String, Any?>.string(key: String) = get(key) as? String
        ?: error("Missing string: $key")
    private fun Map<String, Any?>.boolean(key: String) = get(key) as? Boolean
        ?: error("Missing boolean: $key")
    private fun Map<String, Any?>.int(key: String) = (get(key) as? Number)?.toInt()
        ?: error("Missing integer: $key")
}
