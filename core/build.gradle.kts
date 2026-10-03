plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    // Android-compatible target; keeps :core usable from an Android module later.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Real radio images live in a gitignored folder; tests skip if none is present.
    systemProperty("ht.fixtures", rootProject.file("fixtures/local").absolutePath)
    testLogging { events("passed", "skipped", "failed") }
}
