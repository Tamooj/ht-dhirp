rootProject.name = "ht-dhirp"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android is published only via JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.mik3y") }
        }
    }
}

include(":core")
include(":app")
