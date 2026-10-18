plugins {
    id("org.cyclonedx.bom") version "3.5.1"
    id("com.diffplug.spotless") version "8.10.4"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    alias(libs.plugins.ben.versions)
    alias(libs.plugins.android.application) apply false

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    //    alias(libs.plugins.gms.oss.licenses) apply false
}

buildscript {
    dependencies {
        classpath(libs.plugin.oss.licenses)
    }
}

spotless {
    kotlin {
        target("android/src/**/*.kt", "core/**/src/**/*.kt", "baselineprofile/src/**/*.kt")
        targetExclude("**/build/**")
        ktfmt("0.64").kotlinlangStyle()
    }
    kotlinGradle {
        target(
            "*.gradle.kts",
            "android/*.gradle.kts",
            "core/*/*.gradle.kts",
            "baselineprofile/*.gradle.kts",
        )
        ktfmt("0.64").kotlinlangStyle()
    }
}

detekt {
    source.setFrom(files("android/src/main/java", "core/model/src/main/kotlin"))
    buildUponDefaultConfig = true
    baseline = file("config/detekt-baseline.xml")
    config.setFrom(files("config/detekt.yml"))
}

val numberedRelease =
    providers
        .fileContents(layout.projectDirectory.file("android/build.gradle.kts"))
        .asText
        .get()
        .lineSequence()
        .first { it.contains("const val APP_VERSION_NAME") }
        .substringAfter("=")
        .trim()
        .trim('"')

version = numberedRelease

allprojects {
    group = "com.mattzilla30.mbooru"
    version = numberedRelease
    dependencyLocking { lockAllConfigurations() }
}

allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("releaseRuntimeClasspath", "runtimeClasspath")
        includeLicenseText = true
        licenseChoice =
            org.cyclonedx.model.LicenseChoice().apply {
                addLicense(org.cyclonedx.model.License().apply { id = "GPL-3.0-or-later" })
            }
        includeBomSerialNumber = false
    }
}

// Baseline refresh is an explicit maintenance task, never a dependency of the CI gate.
tasks.named("detekt") { mustRunAfter("detektBaseline", "spotlessApply") }
