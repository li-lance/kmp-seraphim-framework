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
