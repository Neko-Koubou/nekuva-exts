import tasks.ReportGenerateTask
import java.security.MessageDigest

plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp)
}

group = "org.nekosukuriputo"
version = "1.0"

ksp {
    arg("summaryOutputDir", "${projectDir}/.github")
}

val iosGeneratedParserSourcesDir = layout.buildDirectory.dir("generated/iosParserSources/kotlin")

val syncIosParserSources by tasks.registering {
    description = "Syncs JVM parser sources and KSP-generated sources into iOS source set with K/N compatibility transforms."
    dependsOn("kspKotlinJvm")
    val mainSrcDir = file("src/main/kotlin")
    val kspSrcDir = layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/kotlin")
    inputs.dir(mainSrcDir)
    inputs.dir(kspSrcDir)
    outputs.dir(iosGeneratedParserSourcesDir)

    doLast {
        val outRoot = iosGeneratedParserSourcesDir.get().asFile
        if (outRoot.exists()) {
            outRoot.deleteRecursively()
        }
        outRoot.mkdirs()

        val jvmAnnotationLineRegex = Regex("""^\s*@file:JvmName\([^)]*\)\s*$""")
        val inlineJvmAnnotationsRegex = Regex("""@(?:JvmField|JvmStatic|JvmOverloads|JvmSuppressWildcards|JvmWildcard|Synchronized|Volatile)\b\s*""")
        val enumSetNoneOfRegex = Regex("""EnumSet\.noneOf\(([A-Za-z0-9_.]+)::class\.java\)""")
        val enumSetAllOfRegex = Regex("""EnumSet\.allOf\(([A-Za-z0-9_.]+)::class\.java\)""")
        val lruLinkedHashMapRegex = Regex("""object\s*:\s*LinkedHashMap<""")
        val threadLocalWithInitialRegex = Regex("""\bThreadLocal\.withInitial\b""")
        val threadLocalTypeRegex = Regex("""\bThreadLocal<""")

        fun transformKotlinFile(srcFile: java.io.File, destFile: java.io.File) {
            destFile.parentFile.mkdirs()
            val lines = srcFile.readLines()
            val sb = StringBuilder(srcFile.length().toInt() + 128)
            var packageSeen = false
            for (line in lines) {
                if (jvmAnnotationLineRegex.matches(line) || line.trim() == "import kotlin.text.toBigDecimalOrNull") {
                    continue
                }
                var cleaned = inlineJvmAnnotationsRegex.replace(line, "")
                cleaned = enumSetNoneOfRegex.replace(cleaned, "EnumSet.noneOf<$1>()")
                cleaned = enumSetAllOfRegex.replace(cleaned, "EnumSet.allOf<$1>()")
                cleaned = lruLinkedHashMapRegex.replace(cleaned, "object : LruLinkedHashMap<")
                cleaned = threadLocalWithInitialRegex.replace(cleaned, "JavaThreadLocal.withInitial")
                cleaned = threadLocalTypeRegex.replace(cleaned, "JavaThreadLocal<")
                cleaned = cleaned.replace(".location()", ".baseUri()")
                cleaned = cleaned.replace("val branches = ArrayMap<String, String>()", "val branches = ArrayMap<String, String?>()")
                cleaned = cleaned.replace("parseMangaList(data)", "parseMangaList(data ?: org.json.JSONArray())")
                cleaned = cleaned.replace("getTags(jo.optJSONArray(\"tags\"))", "getTags(jo.optJSONArray(\"tags\") ?: org.json.JSONArray())")
                cleaned = cleaned.replace("getTitleTags(jo.optJSONArray(\"genresAndTags\"))", "getTitleTags(jo.optJSONArray(\"genresAndTags\") ?: org.json.JSONArray())")
                sb.append(cleaned).append('\n')
                if (!packageSeen && cleaned.trimStart().startsWith("package ")) {
                    packageSeen = true
                    sb.append("import org.nekosukuriputo.nekuva.ioscompat.*\n")
                    sb.append("import kotlinx.coroutines.IO\n")
                }
            }
            destFile.writeText(sb.toString())
        }

        for ((subName, srcRoot) in listOf("main" to mainSrcDir, "ksp" to kspSrcDir.get().asFile)) {
            if (!srcRoot.exists()) continue
            srcRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relPath = file.relativeTo(srcRoot).path
                    transformKotlinFile(file, outRoot.resolve(subName).resolve(relPath))
                }
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApiWarning()

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=org.nekosukuriputo.nekuva.parsers.InternalParsersApi",
            "-opt-in=kotlin.experimental.ExperimentalNativeApi",
            "-opt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.okio)
                api(libs.androidx.collection)
            }
        }

        val jvmMain by getting {
            kotlin.srcDirs("src/main/kotlin", "build/generated/ksp/jvm/jvmMain/kotlin")
            resources.srcDirs("src/main/resources")
            dependencies {
                implementation(libs.okhttp)
                implementation(libs.json)
                api(libs.jsoup)
            }
        }

        val jvmTest by getting {
            kotlin.srcDirs("src/test/kotlin")
            resources.srcDirs("src/test/resources")
            dependencies {
                implementation(libs.junit.api)
                implementation(libs.junit.engine)
                implementation(libs.junit.params)
                runtimeOnly(libs.junit.launcher)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.quickjs)
            }
        }

        val iosMain by getting {
            kotlin.srcDir(iosGeneratedParserSourcesDir)
            dependencies {
                api(libs.ksoup)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
    }
}

dependencies {
    add("kspJvm", project(":nekuva-ksp"))
}

tasks.matching {
    it.name == "compileKotlinIosArm64" ||
        it.name == "compileKotlinIosSimulatorArm64" ||
        it.name == "compileIosMainKotlinMetadata" ||
        it.name.contains("sourcesJar", ignoreCase = true)
}.configureEach {
    dependsOn(syncIosParserSources)
}

tasks.register<ReportGenerateTask>("generateTestsReport")

// ---- Runtime extension (plugin) artifacts — Step 2: Desktop ------------------------------------
// Produces the loadable Desktop plugin jar (the thin parser bundle; host provides okhttp/jsoup/stdlib +
// the shared contract via parent classloader delegation) plus an `index.json` catalog the host's
// "Update extensions" reads to decide whether to download. Android (dexed) artifact is Step 3.
//
// NOTE: keep `extAbiVersion` in sync with NekuvaExtensions.ABI_VERSION (TODO: generate from one source).
val extAbiVersion = 1

val assemblePluginArtifacts by tasks.registering {
    description = "Assemble the runtime extension (plugin) jar + index.json for the host updater."
    group = "distribution"
    val jarTask = tasks.named<org.gradle.api.tasks.bundling.Jar>("jvmJar")
    dependsOn(jarTask)
    val jarFile = jarTask.flatMap { it.archiveFile }
    val outDir = layout.buildDirectory.dir("plugin")
    val abi = extAbiVersion
    // Release version: CI passes -PpluginVersion=<tag>; falls back to the project version locally.
    val pluginVersion = (project.findProperty("pluginVersion") as String?)?.takeIf { it.isNotBlank() }
        ?: project.version.toString()
    inputs.file(jarFile)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile
        out.mkdirs()
        val target = out.resolve("nekuva-ext-desktop.jar")
        jarFile.get().asFile.copyTo(target, overwrite = true)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString("") { "%02x".format(it) }
        out.resolve("index.json").writeText(
            """
            {
              "abiVersion": $abi,
              "version": "$pluginVersion",
              "artifacts": {
                "desktop": { "file": "${target.name}", "sha256": "$sha" }
              }
            }
            """.trimIndent() + "\n",
        )
        logger.lifecycle("Extension plugin artifacts written to ${out.absolutePath}")
    }
}

// Stages the runtime dependencies (okhttp/jsoup/kotlin-stdlib/…) into build/plugin/deps so CI can pass
// them to `d8 --classpath` when dexing the Android bundle. They're only for reference resolution — the
// host provides them at runtime, so they are NOT included in the produced classes.dex.
val stagePluginDeps by tasks.registering(Copy::class) {
    description = "Copy runtime dependency jars for the Android d8 classpath."
    group = "distribution"
    from(configurations.named("jvmRuntimeClasspath"))
    into(layout.buildDirectory.dir("plugin/deps"))
    include("*.jar")
}
