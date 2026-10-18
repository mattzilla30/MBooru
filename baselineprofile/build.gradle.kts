plugins {
    alias(libs.plugins.android.test)

    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mattzilla30.mbooru.benchmark"
    compileSdk = 36
    targetProjectPath = ":android"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// APK-under-test resolution must use the Android platform consistently for multiplatform XML DTOs.
configurations
    .matching { it.name.endsWith("TestedApks") }
    .configureEach {
        attributes.attribute(
            org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
            org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm,
        )
    }

tasks.register("verifyProfileDependencies") {
    val tested = configurations.named("nonMinifiedReleaseTestedApks")
    inputs.files(tested)
    doLast {
        check(inputs.files.files.isNotEmpty()) { "Profile runtime dependencies did not resolve" }
    }
}
