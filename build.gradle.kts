import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY) }
}

repositories {
    // Google's mirror of Maven Central first: the same artifacts, and it doesn't rate-limit build machines.
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Only the shared IntelliJ Platform is used, so the plugin compiles the same against IntelliJ IDEA and Rider.
        // -PlocalIde=/path/to/an/installed/IDE uses an IDE already on disk instead.
        val localIde = providers.gradleProperty("localIde").orNull
        if (localIde != null) local(localIde) else intellijIdea(providers.gradleProperty("ideaVersion"))
        jetbrainsRuntime()
        pluginVerifier("1.411")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    // Draws the maths (GPL-2.0 with the classpath exception; see THIRD-PARTY-NOTICES.md). Only the main jar: the
    // optional Greek and Cyrillic font add-ons are plain GPL and are not shipped.
    implementation("org.scilab.forge:jlatexmath:1.0.7") { isTransitive = false }
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "261" }
        changeNotes = providers.fileContents(layout.projectDirectory.file("docs/CHANGE-NOTES.html")).asText
    }
    buildSearchableOptions = false

    // Marketplace signing and publishing read their secrets from environment variables only (on GitHub: the
    // repository's Settings > Secrets). Nothing secret is stored in this repository.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // ./gradlew verifyPlugin: JetBrains' plugin checker (the Marketplace's own check) against Rider 2026.1, 2026.2
    // and 2026.3. Each Rider is a ~3 GB download. -PverifyLocalIde=/path checks one installed IDE instead.
    pluginVerification {
        ides {
            val localIde = providers.gradleProperty("verifyLocalIde").orNull
            if (localIde != null) local(localIde) else {
                val riders = providers.gradleProperty("verifyRiders").orElse("2026.1.5.3,2026.2.4.1,2026.3").get()
                for (v in riders.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                    create(IntelliJPlatformType.Rider, v) { useInstaller = false }
            }
        }
    }
}

// The Marketplace lists a plugin for the IDEs whose modules it depends on. plugin.xml only needs the shared
// platform (so tests can run it in the free IntelliJ IDEA). A Marketplace build (-PriderOnly=true) adds Rider as
// a requirement, so it is offered to Rider users only.
tasks.patchPluginXml {
    val riderOnly = providers.gradleProperty("riderOnly").map { it == "true" }.orElse(false)
    inputs.property("riderOnly", riderOnly)
    doLast {
        if (!riderOnly.get()) return@doLast
        val out = outputFile.get().asFile
        out.writeText(out.readText().replaceFirst("<depends>com.intellij.modules.platform</depends>",
            "<depends>com.intellij.modules.platform</depends>\n  <depends>com.intellij.modules.rider</depends>"))
    }
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    useJUnitPlatform { }
    // LATEXCOMMENTS_SHOT_DIR=/path: the rendering tests also write their pictures there (to look at them).
}

// The licence files of JLaTeXMath (and its fonts), taken from the jar that ships, so they always match it.
val jlatexmathNotices by tasks.registering(Sync::class) {
    description = "Collects JLaTeXMath's licence files from its jar."
    val jar = configurations.runtimeClasspath.map { cp -> cp.files.single { it.name.startsWith("jlatexmath-") } }
    from(jar.map { zipTree(it) }) {
        include("META-INF/LICENSE", "META-INF/COPYING", "org/scilab/forge/jlatexmath/fonts/licences/*")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("notices/JLaTeXMath"))
}

// Every licence the plugin zip ships must be present, or the build stops.
val checkNotices by tasks.registering {
    description = "Fails the build if a bundled licence notice is missing."
    dependsOn(jlatexmathNotices)
    val required = listOf("LICENSE", "THIRD-PARTY-NOTICES.md")
    val jlm = layout.buildDirectory.dir("notices/JLaTeXMath")
    doLast {
        for (f in required) check(file(f).isFile && file(f).length() > 0) { "Missing licence file: $f" }
        for (f in listOf("LICENSE", "COPYING", "Knuth_License.txt", "OFL.txt", "License_for_dsrom.txt"))
            check(jlm.get().file(f).asFile.isFile) { "Missing JLaTeXMath licence file: $f" }
    }
}

tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>().configureEach {
    dependsOn(checkNotices)
    from("LICENSE") { into(pluginName.map { "$it/licenses" }) }
    from("THIRD-PARTY-NOTICES.md") { into(pluginName.map { "$it/licenses" }) }
    from(jlatexmathNotices) { into(pluginName.map { "$it/licenses/JLaTeXMath" }) }
}

// UI checks run in the free IntelliJ IDEA (the sandbox Rider needs a JetBrains login, which tests must not do).
//   ./gradlew runIdea -PopenPath=/path/to/project
intellijPlatformTesting {
    runIde {
        register("runIdea") {
            plugins { disablePlugin("com.intellij.platform.daemon") }
            task {
                args(listOfNotNull(providers.gradleProperty("openPath").orNull, providers.gradleProperty("openFile").orNull))
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf("-Dide.show.tips.on.startup.default.value=false", "-Djb.consents.confirmation.enabled=false",
                        "-Djb.privacy.policy.text=<!--999.999-->") +
                    listOfNotNull(providers.gradleProperty("uiScale").orNull?.let { "-Dsun.java2d.uiScale=$it" }) +
                    // Only on a throwaway test machine: trust every project and hide error pop-ups in screenshots.
                    if (providers.gradleProperty("testMachine").isPresent)
                        listOf("-Didea.trust.all.projects=true", "-Didea.fatal.error.notification=disabled") else emptyList()
                }
            }
        }
    }
}

tasks.named("verifyPluginSignature") { dependsOn(tasks.named("signPlugin")) }
