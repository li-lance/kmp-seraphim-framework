# KMP Workbench Phase 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a clean manifest-driven workbench that renders and certifies one Android+iOS local-only daily-board vertical slice without sharing UI code.

**Architecture:** The root build orchestrates a target-neutral included `platform-kit` build and one JVM generator. The generator validates `project.yaml`, renders a product into a sibling temporary directory, performs structural verification, and atomically publishes it. The generated product declares official Android and iOS KMP targets directly; platform-kit supplies policy but never chooses targets.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin 9.1.0, Gradle 9.3.1, JDK 17, Android SDK 37/minSdk 26, Compose BOM 2026.06.00, Xcode 26.4.x, SwiftUI, XcodeGen, SnakeYAML 2.5, kotlin.test, JUnit 5, Gradle TestKit, GitHub Actions.

## Global Constraints

- UI source is never shared between Android and iOS.
- The Android application is a separate `com.android.application` Module.
- Shared Android targets use `com.android.kotlin.multiplatform.library` and `kotlin { android { ... } }`.
- Generated Module files declare their official targets directly; no generic convention plugin may add Android implicitly.
- Kotlin is exactly `2.4.10`; AGP is exactly `9.1.0`; Gradle is exactly `9.3.1`; JDK toolchain is `17`.
- Android `compileSdk` and `targetSdk` are `37`; `minSdk` is `26`.
- Android host tests are enabled explicitly with `withHostTest` and use `androidHostTest`.
- Phase 0 supports exactly Android+iOS with `local-only` data.
- Render verifies structure and tokens before atomic publication; it does not claim platform builds passed.
- Certification runs after publication in a clean generated directory.
- Generation never overwrites a non-empty destination.
- Desktop, Web/Wasm, persistence, Ktor, authentication, synchronization, migration, and standalone CLI export are out of Phase 0 scope.
- The only checked-in dependency version source is `gradle/libs.versions.toml`; platform-kit reads that catalog.
- Root and scoped AI governance follows [AI Governance Design](../specs/2026-08-14-ai-governance-design.md).
- A new `platform-kit/`, `tooling/`, `templates/`, or `products/` source surface includes its scoped `AGENTS.md` in the same task.
- Every task runs `./scripts/check.sh focused <changed-path>...`; Phase 0 completion runs `./scripts/check.sh full`.

Authoritative references:

- [KMP compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android-KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [Jetpack Compose dependency setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)

## Planned File Structure

```text
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
├── platform-kit/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/
├── tooling/generator/
│   ├── build.gradle.kts
│   └── src/
├── templates/daily-board-base/
├── products/daily-board/
├── project.yaml
├── scripts/certify-generated-product.sh
└── .github/workflows/certify-phase-0.yml
```

---

### Task 1: Establish the Locked Workbench Build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Produces: one root version catalog and Gradle 9.3.1 wrapper configuration.
- Produces: included build name `platform-kit` and JVM project `:tooling:generator`.
- Consumes: no implementation files from later tasks.

- [ ] **Step 1: Add the root settings file**

```kotlin
// settings.gradle.kts
pluginManagement {
    if (file("platform-kit/settings.gradle.kts").isFile) {
        includeBuild("platform-kit")
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-seraphim-framework"
include(":tooling:generator")

if (file("products/daily-board/settings.gradle.kts").isFile) {
    includeBuild("products/daily-board")
}
```

- [ ] **Step 2: Add the single version catalog**

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.4.10"
agp = "9.1.0"
composeBom = "2026.06.00"
activityCompose = "1.12.2"
snakeyaml = "2.5"
junit = "5.14.1"

[libraries]
kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
android-gradle-plugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
snakeyaml = { module = "org.yaml:snakeyaml", version.ref = "snakeyaml" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

- [ ] **Step 3: Add root build and Gradle policy**

```kotlin
// build.gradle.kts
plugins {
    base
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
kotlin.code.style=official
android.useAndroidX=true
android.nonTransitiveRClass=true
android.newDsl=true
android.builtInKotlin=true
```

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 4: Generate and verify the wrapper**

Run:

```bash
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
./gradlew --version
```

Expected: the second command reports Gradle `9.3.1` and JVM `17` or newer.

- [ ] **Step 5: Commit the build baseline**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat
git commit -m "build: establish KMP workbench toolchain"
```

---

### Task 2: Add Target-Neutral Platform Policy

**Files:**
- Create: `platform-kit/AGENTS.md`
- Create: `platform-kit/settings.gradle.kts`
- Create: `platform-kit/build.gradle.kts`
- Create: `platform-kit/src/main/kotlin/com/seraphim/workbench/build/Toolchain.kt`
- Create: `platform-kit/src/main/kotlin/com/seraphim/workbench/build/KotlinPolicyPlugin.kt`
- Create: `platform-kit/src/main/kotlin/com/seraphim/workbench/build/AndroidApplicationPlugin.kt`
- Create: `platform-kit/src/test/kotlin/com/seraphim/workbench/build/ConventionPluginsTest.kt`

**Interfaces:**
- Produces: plugin IDs `seraphim.kotlin-policy` and `seraphim.android-application`.
- Produces: `Toolchain.JAVA`, `COMPILE_SDK`, `TARGET_SDK`, and `MIN_SDK`.
- Consumes: the root `gradle/libs.versions.toml` catalog.

- [ ] **Step 1: Write failing convention tests**

```kotlin
// platform-kit/src/test/kotlin/com/seraphim/workbench/build/ConventionPluginsTest.kt
package com.seraphim.workbench.build

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConventionPluginsTest {
    @Test
    fun `kotlin policy does not select Android`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinPolicyPlugin::class.java)

        assertFalse(project.pluginManager.hasPlugin("com.android.kotlin.multiplatform.library"))
    }

    @Test
    fun `toolchain values match compatibility lock`() {
        assertEquals(17, Toolchain.JAVA)
        assertEquals(37, Toolchain.COMPILE_SDK)
        assertEquals(37, Toolchain.TARGET_SDK)
        assertEquals(26, Toolchain.MIN_SDK)
    }

    @Test
    fun `Android application policy does not apply the application plugin`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(AndroidApplicationPlugin::class.java)

        assertFalse(project.pluginManager.hasPlugin("com.android.application"))
    }
}
```

- [ ] **Step: Add platform-kit instructions**

Create `platform-kit/AGENTS.md`:

```markdown
# Platform Kit Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing build policy.

- Platform-kit owns compiler, build, quality, and test policy; it never selects Product platforms or Module topology.
- Convention plugins configure an already selected official Kotlin or Android plugin and must not apply platform plugins implicitly.
- Toolchain values come from the root version catalog; do not duplicate dependency versions.
- Test policy with Gradle TestKit and focused plugin tests before running affected generated-Product certification.

Run `../scripts/check.sh focused platform-kit` and `../gradlew -p platform-kit test` for platform-kit changes.
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew -p platform-kit test
```

Expected: compilation fails because the convention classes do not exist.

- [ ] **Step 3: Configure the included build**

```kotlin
// platform-kit/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "platform-kit"
```

```kotlin
// platform-kit/build.gradle.kts
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("kotlinPolicy") {
            id = "seraphim.kotlin-policy"
            implementationClass = "com.seraphim.workbench.build.KotlinPolicyPlugin"
        }
        register("androidApplication") {
            id = "seraphim.android-application"
            implementationClass = "com.seraphim.workbench.build.AndroidApplicationPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Implement the policy plugins**

```kotlin
// platform-kit/src/main/kotlin/com/seraphim/workbench/build/Toolchain.kt
package com.seraphim.workbench.build

object Toolchain {
    const val JAVA = 17
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 26
}
```

```kotlin
// platform-kit/src/main/kotlin/com/seraphim/workbench/build/KotlinPolicyPlugin.kt
package com.seraphim.workbench.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinPolicyPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure(KotlinMultiplatformExtension::class.java) {
                jvmToolchain(Toolchain.JAVA)
            }
        }
    }
}
```

```kotlin
// platform-kit/src/main/kotlin/com/seraphim/workbench/build/AndroidApplicationPlugin.kt
package com.seraphim.workbench.build

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("com.android.application") {
            extensions.configure(ApplicationExtension::class.java) {
                compileSdk = Toolchain.COMPILE_SDK
                defaultConfig {
                    minSdk = Toolchain.MIN_SDK
                    targetSdk = Toolchain.TARGET_SDK
                }
                buildFeatures.compose = true
            }
        }
    }
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
./gradlew -p platform-kit test
```

Expected: all convention tests pass and applying `seraphim.kotlin-policy` does not apply an Android plugin.

```bash
git add platform-kit
git commit -m "build: add target-neutral platform policy"
```

---

### Task 3: Implement Manifest Parsing and Validation

**Files:**
- Create: `tooling/AGENTS.md`
- Create: `tooling/generator/build.gradle.kts`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ProjectManifest.kt`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ManifestReader.kt`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ManifestValidator.kt`
- Create: `tooling/generator/src/test/kotlin/com/seraphim/workbench/manifest/ManifestTest.kt`

**Interfaces:**
- Produces: `ProjectManifest`, `ManifestReader.read(Path)`, and `ManifestValidator.requireSupported(ProjectManifest)`.
- Consumes: SnakeYAML and the Phase 0 Android+iOS/local-only constraints.

- [ ] **Step 1: Configure the generator Module**

```kotlin
// tooling/generator/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.snakeyaml)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

application {
    mainClass = "com.seraphim.workbench.generator.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write failing manifest tests**

```kotlin
// tooling/generator/src/test/kotlin/com/seraphim/workbench/manifest/ManifestTest.kt
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
```

- [ ] **Step: Add tooling instructions**

Create `tooling/AGENTS.md`:

```markdown
# Tooling Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing the Manifest or generator.

- Validate the complete Manifest before writing Product files.
- Keep parsing, validation, resolution, rendering, structural verification, and publication as independently testable responsibilities.
- Render into a sibling temporary directory and publish atomically only after structural verification.
- Never overwrite a non-empty destination or claim that Render certified a platform build.
- Reject unsupported combinations explicitly; do not silently drop a requested platform or capability.

Run `../scripts/check.sh focused tooling` and `../gradlew :tooling:generator:test` for tooling changes.
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :tooling:generator:test --tests '*ManifestTest'
```

Expected: compilation fails because manifest types do not exist.

- [ ] **Step 4: Implement manifest types, parser, and validator**

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ProjectManifest.kt
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
```

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ManifestReader.kt
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
```

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ManifestValidator.kt
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
```

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :tooling:generator:test --tests '*ManifestTest'
git add tooling/generator
git commit -m "feat: validate Phase 0 product manifests"
```

Expected: manifest tests pass before the commit is created.

---

### Task 4: Implement Structural Render Transactions

**Files:**
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/RenderRequest.kt`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/ProductRenderer.kt`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/GeneratedTreeVerifier.kt`
- Create: `tooling/generator/src/test/kotlin/com/seraphim/workbench/generator/ProductRendererTest.kt`

**Interfaces:**
- Produces: `ProductRenderer.render(RenderRequest): Path`.
- Produces: a structural transaction that never overwrites a non-empty destination.
- Consumes: a validated `ProjectManifest`, one template directory, and an explicit platform-kit path token.

- [ ] **Step 1: Write failing renderer tests**

```kotlin
// tooling/generator/src/test/kotlin/com/seraphim/workbench/generator/ProductRendererTest.kt
package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.Data
import com.seraphim.workbench.manifest.Platforms
import com.seraphim.workbench.manifest.Product
import com.seraphim.workbench.manifest.ProjectManifest
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir

class ProductRendererTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `renders tokens and publishes the directory`() {
        val template = directory.resolve("template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("rootProject.name = \"__PRODUCT_ID__\"")
        template.resolve("project.yaml").writeText("package: __PACKAGE_NAME__\nkit: __PLATFORM_KIT_PATH__")
        val output = directory.resolve("daily-board")

        ProductRenderer.render(RenderRequest(manifest(), template, output, "../../platform-kit"))

        assertEquals("rootProject.name = \"daily-board\"", output.resolve("settings.gradle.kts").readText())
        assertFalse(output.resolve("project.yaml").readText().contains("__"))
    }

    @Test
    fun `does not modify a non-empty destination`() {
        val template = directory.resolve("template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("rootProject.name = \"__PRODUCT_ID__\"")
        val output = directory.resolve("daily-board").createDirectories()
        output.resolve("owned.txt").writeText("keep")

        assertFailsWith<IllegalStateException> {
            ProductRenderer.render(RenderRequest(manifest(), template, output, "../../platform-kit"))
        }

        assertEquals("keep", output.resolve("owned.txt").readText())
        assertFalse(output.resolve("project.yaml").exists())
    }

    private fun manifest() = ProjectManifest(
        schema = 1,
        product = Product("daily-board", "com.seraphim.dailyboard"),
        platforms = Platforms(true, true, false, false),
        data = Data("local-only"),
    )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :tooling:generator:test --tests '*ProductRendererTest'
```

Expected: compilation fails because renderer types do not exist.

- [ ] **Step 3: Implement structural verification**

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/GeneratedTreeVerifier.kt
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
```

- [ ] **Step 4: Implement temporary rendering and atomic publication**

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/RenderRequest.kt
package com.seraphim.workbench.generator

import com.seraphim.workbench.manifest.ProjectManifest
import java.nio.file.Path

data class RenderRequest(
    val manifest: ProjectManifest,
    val template: Path,
    val output: Path,
    val platformKitPath: String,
)
```

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/ProductRenderer.kt
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
```

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :tooling:generator:test --tests '*ProductRendererTest'
git add tooling/generator
git commit -m "feat: render products as structural transactions"
```

Expected: renderer tests pass; no test claims to compile a platform during render.

---

### Task 5: Add the Daily-Board Android+iOS Template

**Files:**
- Create: `templates/AGENTS.md`
- Create: `templates/daily-board-base/settings.gradle.kts`
- Create: `templates/daily-board-base/build.gradle.kts`
- Create: `templates/daily-board-base/gradle.properties`
- Create: `templates/daily-board-base/gradle/libs.versions.toml`
- Create: `templates/daily-board-base/shared/task-board/build.gradle.kts`
- Create: `templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoard.kt`
- Create: `templates/daily-board-base/shared/task-board/src/iosMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardIosAdapter.kt`
- Create: `templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardTest.kt`
- Create: `templates/daily-board-base/apps/android/build.gradle.kts`
- Create: `templates/daily-board-base/apps/android/src/main/AndroidManifest.xml`
- Create: `templates/daily-board-base/apps/android/src/main/res/values/styles.xml`
- Create: `templates/daily-board-base/apps/android/src/main/kotlin/__PACKAGE_PATH__/android/MainActivity.kt`
- Create: `templates/daily-board-base/apps/ios/project.yml`
- Create: `templates/daily-board-base/apps/ios/Sources/DailyBoardApp.swift`
- Create: `templates/daily-board-base/apps/ios/Sources/ContentView.swift`

**Interfaces:**
- Produces: `TaskBoard.createTask(String): TaskBoardSnapshot` and `TaskBoard.snapshot()`.
- Produces: framework `TaskBoardShared` for `iosArm64` and `iosSimulatorArm64`.
- Produces: separate Compose and SwiftUI source trees.
- Consumes: the two platform-kit plugin IDs and renderer tokens.

- [ ] **Step: Add template instructions**

Create `templates/AGENTS.md`:

```markdown
# Template Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Templates are promoted only after a maintained Product proves the structure and a clean generated copy is certified.

- Keep template output deterministic for the same Manifest, template catalog, and toolchain lock.
- Use explicit replacement tokens and verify that no unresolved token reaches published output.
- Emit only selected platforms and capabilities.
- Do not place Product-specific roadmap phases or unproven shared abstractions into templates.
- A rendered tree proves structure; only Certify proves supported platform builds.

Run `../scripts/check.sh focused templates`, generator tests, and the certification commands for every claimed template combination.
```

- [ ] **Step 1: Add product build files**

```kotlin
// templates/daily-board-base/settings.gradle.kts
pluginManagement {
    includeBuild("__PLATFORM_KIT_PATH__")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "__PRODUCT_ID__"
include(":shared:task-board", ":apps:android")
```

```kotlin
// templates/daily-board-base/build.gradle.kts
plugins { base }
```

Copy the locked root files without changing their contents:

```bash
mkdir -p templates/daily-board-base/gradle
cp gradle.properties templates/daily-board-base/gradle.properties
cp gradle/libs.versions.toml templates/daily-board-base/gradle/libs.versions.toml
```

- [ ] **Step 2: Add the vertical shared Module**

```kotlin
// templates/daily-board-base/shared/task-board/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    id("seraphim.kotlin-policy")
}

kotlin {
    android {
        namespace = "__PACKAGE_NAME__.taskboard"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    iosArm64 {
        binaries.framework {
            baseName = "TaskBoardShared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "TaskBoardShared"
            isStatic = true
        }
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        androidHostTest.dependencies { implementation(kotlin("test")) }
    }
}
```

```kotlin
// templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoard.kt
package __PACKAGE_NAME__.taskboard

data class TaskItem(val id: Long, val title: String)
data class TaskBoardSnapshot(val tasks: List<TaskItem>)

class TaskBoard {
    private val tasks = mutableListOf<TaskItem>()

    fun createTask(title: String): TaskBoardSnapshot {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "Task title must not be blank" }
        tasks += TaskItem(id = tasks.size.toLong() + 1, title = normalized)
        return snapshot()
    }

    fun snapshot(): TaskBoardSnapshot = TaskBoardSnapshot(tasks.toList())
}
```

```kotlin
// templates/daily-board-base/shared/task-board/src/iosMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardIosAdapter.kt
package __PACKAGE_NAME__.taskboard

class TaskBoardIosAdapter {
    private val board = TaskBoard()

    fun createTaskTitle(title: String): String = board.createTask(title).tasks.last().title
}
```

```kotlin
// templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardTest.kt
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskBoardTest {
    @Test
    fun `creates a trimmed task`() {
        val board = TaskBoard()
        assertEquals("Read", board.createTask("  Read  ").tasks.single().title)
    }
}
```

- [ ] **Step 3: Add the Android Compose entry point**

```kotlin
// templates/daily-board-base/apps/android/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("seraphim.android-application")
}

android {
    namespace = "__PACKAGE_NAME__.android"
    defaultConfig {
        applicationId = "__PACKAGE_NAME__.android"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":shared:task-board"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
}
```

```xml
<!-- templates/daily-board-base/apps/android/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:theme="@style/AppTheme" android:label="Daily Board">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

```xml
<!-- templates/daily-board-base/apps/android/src/main/res/values/styles.xml -->
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar" />
</resources>
```

```kotlin
// templates/daily-board-base/apps/android/src/main/kotlin/__PACKAGE_PATH__/android/MainActivity.kt
package __PACKAGE_NAME__.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import __PACKAGE_NAME__.taskboard.TaskBoard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshot = TaskBoard().createTask("First task")
        setContent {
            MaterialTheme { Text(snapshot.tasks.single().title) }
        }
    }
}
```

- [ ] **Step 4: Add the SwiftUI entry point**

```yaml
# templates/daily-board-base/apps/ios/project.yml
name: DailyBoard
options:
  bundleIdPrefix: __PACKAGE_NAME__
targets:
  DailyBoard:
    type: application
    platform: iOS
    deploymentTarget: "17.0"
    sources: [Sources]
    dependencies:
      - framework: ../../shared/task-board/build/bin/iosSimulatorArm64/debugFramework/TaskBoardShared.framework
        embed: false
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: __PACKAGE_NAME__.ios
        CODE_SIGNING_ALLOWED: NO
```

```swift
// templates/daily-board-base/apps/ios/Sources/DailyBoardApp.swift
import SwiftUI

@main
struct DailyBoardApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

```swift
// templates/daily-board-base/apps/ios/Sources/ContentView.swift
import SwiftUI
import TaskBoardShared

struct ContentView: View {
    private let title = TaskBoardIosAdapter().createTaskTitle(title: "First task")

    var body: some View {
        Text(title)
            .padding()
    }
}
```

- [ ] **Step 5: Commit the product template**

```bash
git add templates/daily-board-base
git commit -m "feat: add native UI daily-board template"
```

Expected: the template contains unresolved tokens only under `templates/daily-board-base` and contains no shared UI directory.

---

### Task 6: Wire the Gradle Render Entry Point

**Files:**
- Create: `products/AGENTS.md`
- Create: `tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/Main.kt`
- Modify: `build.gradle.kts`
- Create: `project.yaml`
- Generate: `products/daily-board/`

**Interfaces:**
- Produces: `./gradlew createProduct -Pmanifest=... -Poutput=...`.
- Consumes: `ManifestReader`, `ManifestValidator`, `ProductRenderer`, and `templates/daily-board-base`.

- [ ] **Step 1: Add the generator command**

```kotlin
// tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/Main.kt
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
```

- [ ] **Step 2: Register the root task**

Replace the root `build.gradle.kts` with:

```kotlin
import org.gradle.api.tasks.SourceSetContainer

plugins { base }

val generator = project(":tooling:generator")

tasks.register<JavaExec>("createProduct") {
    group = "workbench"
    description = "Render a Phase 0 product"
    dependsOn(":tooling:generator:classes")
    mainClass.set("com.seraphim.workbench.generator.MainKt")
    classpath = generator.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
    args(
        "--manifest", providers.gradleProperty("manifest").orElse("project.yaml").get(),
        "--template", layout.projectDirectory.dir("templates/daily-board-base").asFile.absolutePath,
        "--output", providers.gradleProperty("output").orElse("products/daily-board").get(),
        "--platform-kit", layout.projectDirectory.dir("platform-kit").asFile.absolutePath,
    )
}
```

- [ ] **Step 3: Add the reference manifest**

```yaml
# project.yaml
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
```

- [ ] **Step: Add Product instructions**

Create `products/AGENTS.md`:

```markdown
# Product Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Every Product is independently buildable and may add a Product-local `AGENTS.md` when it gains rules not shared by sibling Products.

- Platform applications own UI, navigation, lifecycle, accessibility, theme, permissions, and presentation state.
- Shared Kotlin owns business rules and narrow platform-facing commands, snapshots, failures, and event streams; it never owns platform UI.
- Unselected platforms do not appear in the Product settings or Gradle Module graph.
- Product source does not reach into Workbench tooling internals; exported Products consume only their locked platform-kit and generated metadata.
- Preserve local-first behavior until a Product explicitly selects and implements backend capabilities.

Run `../scripts/check.sh focused products` plus the Product's selected platform tests and certification commands.
```

- [ ] **Step 4: Run all generator tests and render the reference product**

```bash
./gradlew :tooling:generator:test
./gradlew createProduct -Pmanifest=project.yaml -Poutput=products/daily-board
```

Expected: tests pass and the second command prints `Rendered daily-board` without compiling Android or iOS.

- [ ] **Step 5: Commit the entry point and rendered reference product**

```bash
git add build.gradle.kts project.yaml tooling/generator products/daily-board
git commit -m "feat: render the daily-board reference product"
```

---

### Task 7: Add Clean Product Certification

**Files:**
- Create: `scripts/certify-generated-product.sh`
- Create: `.github/workflows/certify-phase-0.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`

**Interfaces:**
- Produces: local structural and Android certification command.
- Produces: macOS iOS framework and SwiftUI application certification.
- Consumes: the root `createProduct` task and generated product build interfaces.

- [ ] **Step 1: Add the local certification script**

```bash
#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
fixture_root="$repository_root/build/certification/daily-board"

rm -rf "$fixture_root"
"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/project.yaml" \
  -Poutput="$fixture_root"

"$repository_root/gradlew" \
  -p "$fixture_root" \
  :shared:task-board:allTests \
  :apps:android:assembleDebug
```

Make it executable:

```bash
chmod +x scripts/certify-generated-product.sh
```

- [ ] **Step 2: Run local certification**

```bash
scripts/certify-generated-product.sh
```

Expected: a fresh fixture is rendered, shared tests pass, Android host tests pass, and the debug APK assembles.

- [ ] **Step: Publish the implemented Phase 0 architecture**

In `docs/architecture.md`, replace `## Current state` and `## Target composition` with:

````markdown
## Current state

Phase 0 implements the root orchestration build, target-neutral platform-kit, Manifest validation, structural Render transaction, Android+iOS daily-board template, maintained reference Product, and clean certification entry point. Desktop, Web/Wasm, persistence, backend, authentication, synchronization, migration, and standalone CLI export remain outside the implemented baseline.

## Phase 0 composition

```text
project.yaml
    ↓
tooling/generator → products/daily-board
        ↑                    ↓
templates/           Android and iOS certification
        ↑
platform-kit supplies build policy without selecting Product topology
```

- The Workbench root orchestrates generation and certification.
- `tooling/generator` validates the Manifest, resolves the template, renders into a sibling temporary directory, verifies structure, and publishes atomically.
- `platform-kit/` supplies compiler, build, quality, and test policy through an included build.
- `templates/daily-board-base/` owns the certified Android+iOS template.
- `products/daily-board/` is the independently buildable reference Product.

The approved detailed design is [KMP Multi-Project Workbench Design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md). [CONTEXT.md](../CONTEXT.md) owns domain definitions and invariants.
````

Include `docs/architecture.md` in Task 7's commit so shipped composition and certification evidence cannot diverge.

- [ ] **Step 3: Add CI certification**

```yaml
# .github/workflows/certify-phase-0.yml
name: Certify Phase 0

on:
  pull_request:
  push:
    branches: [master]

permissions:
  contents: read

jobs:
  generator-and-android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"
      - uses: android-actions/setup-android@v3
      - run: sdkmanager "platforms;android-37" "build-tools;36.0.0"
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew -p platform-kit test
      - run: ./gradlew :tooling:generator:test
      - run: scripts/certify-generated-product.sh

  ios:
    runs-on: macos-26
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v6
      - run: brew install xcodegen
      - run: ./gradlew createProduct -Pmanifest=project.yaml -Poutput=build/certification-ios
      - run: ./gradlew -p build/certification-ios :shared:task-board:linkDebugFrameworkIosSimulatorArm64
      - run: xcodegen generate --spec build/certification-ios/apps/ios/project.yml
      - run: >-
          xcodebuild
          -project build/certification-ios/apps/ios/DailyBoard.xcodeproj
          -scheme DailyBoard
          -sdk iphonesimulator
          -destination 'generic/platform=iOS Simulator'
          CODE_SIGNING_ALLOWED=NO
          build
```

- [ ] **Step 4: Document verification commands**

Append to `README.md`:

```markdown
## Verify Phase 0

```bash
./gradlew -p platform-kit test
./gradlew :tooling:generator:test
scripts/certify-generated-product.sh
```

The iOS certification command is encoded in `.github/workflows/certify-phase-0.yml` and runs on `macos-26`.
```

- [ ] **Step 5: Run final checks and commit**

```bash
./gradlew -p platform-kit test
./gradlew :tooling:generator:test
scripts/certify-generated-product.sh
git diff --check
rg -n '__[A-Z_]+__' products tooling platform-kit || true
git status --short
```

Expected:

- every Gradle and script command exits `0`;
- `git diff --check` prints nothing;
- unresolved tokens exist only under `templates/daily-board-base`;
- `.idea/` remains untracked and unstaged.

```bash
git add .github/workflows/certify-phase-0.yml scripts/certify-generated-product.sh README.md docs/architecture.md
git commit -m "ci: certify generated Android and iOS products"
```

## Phase 0 Completion Gate

Run:

```bash
./scripts/check.sh full
```

Expected: every governance check reports `PASS`; no scoped instruction remains `UNAVAILABLE` after all four source surfaces exist.

Phase 0 is complete only when:

1. The platform-kit tests prove the Kotlin policy does not select Android.
2. Manifest and renderer tests pass.
3. A clean Android+iOS product is rendered without modifying a non-empty destination.
4. Android host tests and assemble pass.
5. The iOS framework links and the SwiftUI application builds on CI.
6. Render and certification remain separate commands.
7. No Desktop, Web/Wasm, backend, persistence, synchronization, or standalone export implementation is present.
