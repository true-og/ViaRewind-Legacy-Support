import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar
import org.gradle.process.ExecOperations
import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    id("java-library")
    id("maven-publish")
    id("eclipse")
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
}

repositories {
    mavenLocal()
    maven("https://repo.viaversion.com")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.maven.apache.org/maven2/")
}

val maven_version: String by project
val maven_group: String by project

group = maven_group
version = maven_version

dependencies {
    compileOnly("com.viaversion:viaversion-api:4.10.0")
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

val pluginVersion = version.toString()

tasks.named<ProcessResources>("processResources") {
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") { expand("version" to pluginVersion) }
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<Javadoc> { options.encoding = "UTF-8" }

fun capture(vararg cmd: String): String =
    providers.exec {
        commandLine(*cmd)
    }.standardOutput.asText.get().trim()

val latestCommitHash   = capture("git", "rev-parse", "--short", "HEAD")
val latestCommitMessage = capture("git", "log", "-1", "--pretty=%B")
val branchName         = capture("git", "rev-parse", "--abbrev-ref", "HEAD")

val baseVersion = maven_version
val isRelease = !baseVersion.contains('-')
val suffixedVersion = if (isRelease) baseVersion else "$baseVersion+${System.getenv("GITHUB_RUN_NUMBER") ?: ""}"
val changelogContent = "[${latestCommitHash}](https://github.com/ViaVersion/ViaRewind-Legacy-Support/commit/${latestCommitHash}) $latestCommitMessage"
val isMainBranch = branchName == "master"

hangarPublish {
    publications.register("plugin") {
        id.set("ViaRewindLegacySupport")
        version.set(suffixedVersion)
        channel.set(
            when {
                isRelease -> "Release"
                isMainBranch -> "Snapshot"
                else -> "Alpha"
            }
        )
        changelog.set(changelogContent)
        apiKey.set(System.getenv("HANGAR_TOKEN"))
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
                platformVersions.set(listOf(property("mcVersionRange") as String))
                dependencies {
                    hangar("ViaVersion") { required.set(true) }
                    hangar("ViaBackwards") { required.set(false) }
                    hangar("ViaRewind") { required.set(false) }
                }
            }
        }
    }
}

