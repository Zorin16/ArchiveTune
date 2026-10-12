import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val javaFxVersion = "21.0.6"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val javaFxPlatform = when {
    osName.contains("win") -> "win"
    osName.contains("mac") && (osArch.contains("aarch64") || osArch.contains("arm64")) -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    (osArch.contains("aarch64") || osArch.contains("arm64")) -> "linux-aarch64"
    else -> "linux"
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "moe.rukamori.archivetune.desktop.MainKt"
        nativeDistributions {
            packageName = "ArchiveTune"
            packageVersion = "15.1.0"
            description = "ArchiveTune music player for desktop"
            vendor = "morieattonkatsu"
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg)
            windows {
                menuGroup = "ArchiveTune"
                upgradeUuid = "cf548b51-55ab-4d37-9a66-89cce0ce880d"
            }
            macOS {
                bundleID = "moe.rukamori.archivetune.desktop"
            }
            linux {
                packageName = "archivetune"
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core"))
    implementation(project(":lastfm"))
    implementation(project(":lyrics:lrclib"))
    implementation(project(":lyrics:kugou"))
    implementation(project(":lyrics:simpmusic"))
    implementation(project(":lyrics:paxsenix"))
    implementation(project(":lyrics:unison"))
    implementation(project(":lyrics:betterlyrics"))
    implementation(project(":lyrics:youlyplus"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.core)
    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.ibm.icu:icu4j:78.3")
    implementation(libs.ktor.serialization.json)

    listOf("base", "graphics", "media").forEach { module ->
        implementation("org.openjfx:javafx-$module:$javaFxVersion:$javaFxPlatform")
    }
}
