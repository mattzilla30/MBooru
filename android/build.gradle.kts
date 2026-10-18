/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    id(libs.plugins.gms.oss.licenses.get().pluginId)
}

// Supply a google-services.json registered for com.mattzilla30.mbooru to enable Firebase.
val firebaseEnabled =
    file("google-services.json").exists() &&
        providers.gradleProperty("enableFirebase").orNull == "true"

if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val releaseStoreFile = file("../.gradle/mbooru-release.jks")
val storePropertyFile = file("../.gradle/keystore.properties")

val properties = Properties()

if (storePropertyFile.exists()) {
    storePropertyFile.inputStream().use(properties::load)
}

object Configs {
    const val APP_VERSION_NAME = "2"
    const val APP_VERSION_CODE = 1375
    const val APP_ID = "com.mattzilla30.mbooru"
    const val COMPILE_SDK_VERSION = 36
    const val TARGET_SDK_VERSION = 36
    const val MIN_SDK_VERSION = 21

    val LANGUAGES =
        listOf(
            "en",
            "zh-rCN",
            "zh-rHK",
            "zh-rTW",
            "nl-rNL",
            "pt-rBR",
            "es-rES",
            "pl-rPL",
            "fr-rFR",
            "hu-rHU",
            "ru-rRU",
            "ja-rJP",
            "in-rID",
            "de-rDE",
            "tr-rTR",
        )
}

android {
    sourceSets.maybeCreate("nonMinifiedRelease")
    sourceSets.getByName("test").resources.directories.add("$projectDir/schemas")
    signingConfigs {
        if (storePropertyFile.exists() && releaseStoreFile.exists()) {
            create("release") {
                storeFile = releaseStoreFile
                keyAlias = properties.getProperty("KEY_ALIAS")
                keyPassword = properties.getProperty("KEY_PASS")
                storePassword = properties.getProperty("STORE_PASS")
            }
        }
    }
    compileSdk =
        providers
            .gradleProperty("androidCompileSdk")
            .map(String::toInt)
            .getOrElse(Configs.COMPILE_SDK_VERSION)
    androidResources { localeFilters.addAll(Configs.LANGUAGES) }
    defaultConfig {
        applicationId = Configs.APP_ID
        minSdk = Configs.MIN_SDK_VERSION
        //noinspection EditedTargetSdkVersion
        targetSdk =
            providers
                .gradleProperty("androidTargetSdk")
                .map(String::toInt)
                .getOrElse(Configs.TARGET_SDK_VERSION)
        versionCode = Configs.APP_VERSION_CODE
        versionName = Configs.APP_VERSION_NAME
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AGP 8.9's lint model still reads this list rather than androidResources.localeFilters.
    }
    buildTypes {
        release {
            // Source provenance is recorded by the published release tag.
            vcsInfo { include = false }
            isShrinkResources = true
            isMinifyEnabled = true
            val config =
                try {
                    signingConfigs.getByName("release")
                } catch (_: UnknownDomainObjectException) {
                    null
                }
            if (config != null) {
                signingConfig = config
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            val config =
                try {
                    signingConfigs.getByName("release")
                } catch (_: UnknownDomainObjectException) {
                    null
                }
            if (config != null) {
                signingConfig = config
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    namespace = "com.mattzilla30.mbooru"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:model"))
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)

    //    coreLibraryDesugaring(libs.desugarJdkLibs)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.ui)
    implementation(libs.compose.foundation.foundation)
    implementation(libs.compose.material3.material3)
    implementation(libs.compose.material.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.material.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.browser)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.navigation.runtime.ktx)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.muzei.api)
    implementation(libs.photoview)
    implementation(libs.subsampling.scale.image.view)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.barcode.scanning)

    if (firebaseEnabled) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }

    implementation(libs.gms.oss.licenses)

    implementation(libs.retrofit.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okio)

    implementation(libs.coil.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(libs.xmlutil.android.core)
    implementation(libs.xmlutil.android.serialization)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.test.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.test.robolectric)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

baselineProfile { automaticGenerationDuringBuild = false }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.ENABLE)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=androidx.paging.ExperimentalPagingApi",
            "-opt-in=nl.adaptivity.xmlutil.ExperimentalXmlUtilApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
        )
    }
}

// The profile plugin copies the release manifest when creating its synthetic build type.
// Restore the offline fixture manifest after that copy; production release stays unchanged.
androidComponents {
    finalizeDsl {
        it.sourceSets
            .getByName("nonMinifiedRelease")
            .manifest
            .srcFile("src/nonMinifiedRelease/AndroidManifest.xml")
    }
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        val merged =
            variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        tasks.register("verifyOfflineProfileManifest") {
            inputs.file(merged)
            doLast {
                check(
                    merged
                        .get()
                        .asFile
                        .readText()
                        .contains("com.mattzilla30.mbooru.benchmark.FixtureApp")
                ) {
                    "Profile target must use the offline fixture application"
                }
            }
        }
    }
}
