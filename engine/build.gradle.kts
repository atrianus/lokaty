import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Testy silnika to programy uruchamiane przez engine/build-and-test.sh (bez JUnit),
// więc Gradle buduje z silnika tylko bibliotekę dla aplikacji.
