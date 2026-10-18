plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies { testImplementation(libs.test.junit) }

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
