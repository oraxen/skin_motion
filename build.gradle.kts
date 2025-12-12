import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
}

val pluginVersion: String by project

allprojects {
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    group = "dev.th0rgal.customcapes"
    version = pluginVersion

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.processResources {
        expand(mapOf("version" to pluginVersion))
    }

    repositories {
        mavenLocal()
        mavenCentral()
        // Paper/Spigot
        maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        // Velocity
        maven { url = uri("https://nexus.velocitypowered.com/repository/maven-public/") }
        // BStats
        maven { url = uri("https://repo.codemc.org/repository/maven-public") }
        // Adventure
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

project(":customcapes-core") {
    dependencies {
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("net.kyori:adventure-api:4.14.0")
        implementation("net.kyori:adventure-text-minimessage:4.14.0")
        implementation("org.yaml:snakeyaml:2.2")
        compileOnly("org.jetbrains:annotations:24.0.1")
    }
}

project(":customcapes-bukkit") {
    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
        compileOnly("org.jetbrains:annotations:24.0.1")
        compileOnly(project(path = ":customcapes-core", configuration = "shadow"))

        implementation("net.kyori:adventure-platform-bukkit:4.3.2")
        implementation("org.bstats:bstats-bukkit:3.0.2")
    }
}

project(":customcapes-bungee") {
    dependencies {
        compileOnly("net.md-5:bungeecord-api:1.20-R0.2")
        compileOnly("org.jetbrains:annotations:24.0.1")
        compileOnly(project(path = ":customcapes-core", configuration = "shadow"))

        implementation("net.kyori:adventure-platform-bungeecord:4.3.2")
        implementation("org.bstats:bstats-bungeecord:3.0.2")
    }
}

project(":customcapes-velocity") {
    dependencies {
        compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
        compileOnly("org.jetbrains:annotations:24.0.1")
        compileOnly(project(path = ":customcapes-core", configuration = "shadow"))
        annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

        implementation("org.bstats:bstats-velocity:3.0.2")
    }
}

tasks.shadowJar {
    relocate("org.bstats", "dev.th0rgal.customcapes.shaded.bstats")
    relocate("net.kyori.adventure.platform.bukkit", "dev.th0rgal.customcapes.shaded.adventure.platform.bukkit")
    relocate("org.yaml.snakeyaml", "dev.th0rgal.customcapes.shaded.snakeyaml")
    
    manifest {
        attributes(
            "Built-By" to System.getProperty("user.name"),
            "Version" to pluginVersion,
            "Build-Timestamp" to SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ").format(Date()),
            "Created-By" to "Gradle ${gradle.gradleVersion}",
            "Build-Jdk" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})",
            "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
        )
    }
    archiveFileName.set("customcapes-${pluginVersion}.jar")
}

dependencies {
    implementation(project(path = ":customcapes-core", configuration = "shadow"))
    implementation(project(path = ":customcapes-bukkit", configuration = "shadow"))
    implementation(project(path = ":customcapes-bungee", configuration = "shadow"))
    implementation(project(path = ":customcapes-velocity", configuration = "shadow"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

