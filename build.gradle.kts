import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("java")
}

val pluginVersion: String by project

allprojects {
    apply(plugin = "java")

    group = "dev.th0rgal.skinmotion"
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
        // Velocity artifacts are served by the Paper repository above (nexus.velocitypowered.com is gone)
        // BStats
        maven { url = uri("https://repo.codemc.org/repository/maven-public") }
        // Adventure
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

project(":skinmotion-core") {
    dependencies {
        implementation("com.google.code.gson:gson:2.10.1")
        implementation("net.kyori:adventure-api:4.14.0")
        implementation("net.kyori:adventure-text-minimessage:4.14.0")
        implementation("org.yaml:snakeyaml:2.2")
        implementation("org.java-websocket:Java-WebSocket:1.5.6")
        compileOnly("org.jetbrains:annotations:24.0.1")

        testImplementation(platform("org.junit:junit-bom:5.10.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

project(":skinmotion-bukkit") {
    apply(plugin = "com.github.johnrengelman.shadow")

    // Keep the unshaded jar from overwriting the shaded plugin jar of the same name
    tasks.named<Jar>("jar") {
        archiveClassifier.set("plain")
    }
    
    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
        compileOnly("org.jetbrains:annotations:24.0.1")
        implementation(project(":skinmotion-core"))

        implementation("net.kyori:adventure-platform-bukkit:4.3.2")
        implementation("org.bstats:bstats-bukkit:3.0.2")
        implementation("org.java-websocket:Java-WebSocket:1.5.6")
        implementation("org.yaml:snakeyaml:2.2")
    }
    
    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        archiveFileName.set("skinmotion-bukkit-${pluginVersion}.jar")
        
        relocate("org.bstats", "dev.th0rgal.skinmotion.shaded.bstats")
        relocate("net.kyori.adventure.platform.bukkit", "dev.th0rgal.skinmotion.shaded.adventure.platform.bukkit")
        relocate("org.yaml.snakeyaml", "dev.th0rgal.skinmotion.shaded.snakeyaml")
        relocate("org.java_websocket", "dev.th0rgal.skinmotion.shaded.websocket")
        
        manifest {
            attributes(
                // Only reflection is used against the server; tell Paper not to remap this jar
                "paperweight-mappings-namespace" to "mojang",
                "Built-By" to System.getProperty("user.name"),
                "Version" to pluginVersion,
                "Build-Timestamp" to SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ").format(Date()),
                "Created-By" to "Gradle ${gradle.gradleVersion}",
                "Build-Jdk" to "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})",
                "Build-OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")} ${System.getProperty("os.version")}"
            )
        }
    }
    
    tasks.named("build") {
        dependsOn(tasks.named("shadowJar"))
    }
}

project(":skinmotion-bungee") {
    apply(plugin = "com.github.johnrengelman.shadow")

    // Keep the unshaded jar from overwriting the shaded plugin jar of the same name
    tasks.named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    dependencies {
        compileOnly("net.md-5:bungeecord-api:1.20-R0.2")
        compileOnly("org.jetbrains:annotations:24.0.1")
        implementation(project(":skinmotion-core"))

        implementation("net.kyori:adventure-platform-bungeecord:4.3.2")
        implementation("net.kyori:adventure-text-minimessage:4.14.0")
        implementation("org.bstats:bstats-bungeecord:3.0.2")
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        archiveFileName.set("skinmotion-bungee-${pluginVersion}.jar")

        // BungeeCord ships Gson; the proxy module never opens the plugin WebSocket
        dependencies {
            exclude(dependency("com.google.code.gson:gson"))
            exclude(dependency("org.java-websocket:Java-WebSocket"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
        relocate("org.bstats", "dev.th0rgal.skinmotion.shaded.bstats")
        relocate("net.kyori", "dev.th0rgal.skinmotion.shaded.kyori")
        relocate("org.yaml.snakeyaml", "dev.th0rgal.skinmotion.shaded.snakeyaml")
    }

    tasks.named("build") {
        dependsOn(tasks.named("shadowJar"))
    }
}

project(":skinmotion-velocity") {
    apply(plugin = "com.github.johnrengelman.shadow")

    // Keep the unshaded jar from overwriting the shaded plugin jar of the same name
    tasks.named<Jar>("jar") {
        archiveClassifier.set("plain")
    }

    dependencies {
        compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
        compileOnly("org.jetbrains:annotations:24.0.1")
        implementation(project(":skinmotion-core"))

        implementation("org.bstats:bstats-velocity:3.0.2")
    }

    tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        archiveFileName.set("skinmotion-velocity-${pluginVersion}.jar")

        // Velocity ships Adventure, MiniMessage and Gson; the proxy module never opens the plugin WebSocket
        dependencies {
            exclude(dependency("com.google.code.gson:gson"))
            exclude(dependency("net.kyori:.*"))
            exclude(dependency("org.java-websocket:Java-WebSocket"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
        relocate("org.bstats", "dev.th0rgal.skinmotion.shaded.bstats")
        relocate("org.yaml.snakeyaml", "dev.th0rgal.skinmotion.shaded.snakeyaml")
    }

    tasks.named("build") {
        dependsOn(tasks.named("shadowJar"))
    }
}

// Root project: collect one loadable jar per platform in build/libs
tasks.register<Copy>("copyPlatformJars") {
    dependsOn(":skinmotion-bukkit:shadowJar", ":skinmotion-bungee:shadowJar", ":skinmotion-velocity:shadowJar")
    from(project(":skinmotion-bukkit").layout.buildDirectory.file("libs/skinmotion-bukkit-${pluginVersion}.jar")) {
        rename { "skinmotion-${pluginVersion}.jar" }
    }
    from(project(":skinmotion-bungee").layout.buildDirectory.file("libs/skinmotion-bungee-${pluginVersion}.jar"))
    from(project(":skinmotion-velocity").layout.buildDirectory.file("libs/skinmotion-velocity-${pluginVersion}.jar"))
    into(layout.buildDirectory.dir("libs"))
}

tasks.named("build") {
    dependsOn("copyPlatformJars")
}
