plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.changelog")
    id("com.google.protobuf") version "0.9.4"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.itangcent"
version = "3.1.9.252.0"

changelog {
    val v = project.version.toString()
    // version is like "3.1.5.252.0", changelog uses semver "3.1.5"
    version.set(v.substringBeforeLast(".0").substringBeforeLast("."))
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.1")
        bundledPlugins("com.intellij.java", "org.jetbrains.idea.maven", "org.jetbrains.plugins.gradle", "org.jetbrains.kotlin", "org.intellij.groovy", "org.intellij.intelliLang")
        plugin("org.intellij.scala:2025.2.51")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
    }

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.12.2")
    implementation("org.xerial:sqlite-jdbc:3.34.0")

    // LangChain4j — AI agent substrate (design §3.0)
    // Explicit versions (avoid BOM — it imposes global kotlin-stdlib constraints
    // that conflict with IntelliJ's bundled Kotlin 2.1+).
    val langchain4jCoreVersion = "1.0.0-rc1"   // core + open-ai
    val langchain4jBetaVersion = "1.0.0-beta4" // anthropic, gemini, ollama, azure
    implementation("dev.langchain4j:langchain4j:$langchain4jCoreVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-core:$langchain4jCoreVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jCoreVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-anthropic:$langchain4jBetaVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:$langchain4jBetaVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jBetaVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-azure-open-ai:$langchain4jBetaVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.slf4j")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    val grpcVersion = "1.68.0"
    val protobufVersion = "3.25.3"
    testImplementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    testImplementation("io.grpc:grpc-api:$grpcVersion")
    testImplementation("io.grpc:grpc-protobuf:$grpcVersion")
    testImplementation("io.grpc:grpc-stub:$grpcVersion")
    testImplementation("io.grpc:grpc-core:$grpcVersion")
    testImplementation("io.grpc:grpc-services:$grpcVersion")
    testImplementation("com.google.protobuf:protobuf-java:$protobufVersion")
    testImplementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
    testImplementation("com.google.guava:guava:33.0.0-jre")
    testImplementation("com.google.guava:failureaccess:1.0.2")
    testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc") {}
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test>().configureEach {
    useJUnit()
    maxParallelForks = 1
    testLogging {
        events("started", "passed", "failed", "skipped")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

}

intellijPlatform {
    pluginConfiguration {
        id = "com.itangcent.idea.plugin.easy-yapi"
        name = "EasyYapi"
        version = project.version.toString()
        description = file("src/main/resources/pluginDescription.html").readText()
        changeNotes = provider {
            changelog.renderItem(
                changelog.getLatest(),
                org.jetbrains.changelog.Changelog.OutputType.HTML
            )
        }
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    buildSearchableOptions = false
    sandboxContainer = layout.projectDirectory.dir("idea-sandbox")
}

kover {
    reports {
        filters {
            excludes {
                classes("jdk.internal.*")
            }
        }
        total {
            xml {
                onCheck = false
            }
            html {
                onCheck = false
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Knowledge-base sync
//
// `docs/knowledge-base/` at the repo root is the single source of truth for
// the plugin documentation consumed by both AI rule-authoring surfaces:
//   1. The built-in IntelliJ agent — reads it from the plugin JAR at runtime
//      via the `get_plugin_doc` tool (every *.md under that folder).
//   2. The external `easy-yapi-assistant` skill — ships a verbatim copy next
//      to its SKILL.md so it works after `npx skills add` (which publishes
//      only the skills/ folder, not the repo tree).
//
// This task copies the canonical files into both destinations. It is wired
// into `processResources` so the JAR always ships fresh docs, and can be run
// standalone (`./gradlew syncKnowledgeBase`) to refresh the git-tracked skill
// copy before commit. The copies are content-equality-checked, so an
// unchanged source produces no git diff (idempotent).
// ─────────────────────────────────────────────────────────────────────────────
val knowledgeBaseSourceDir = file("docs/knowledge-base")
val knowledgeBaseResourceDir = file("src/main/resources/docs/knowledge-base")
// The skill bundles its docs under a `docs/` subfolder next to SKILL.md to
// keep the skill root tidy (SKILL.md + docs/ + scripts/).
val knowledgeBaseSkillDir = file("skills/easy-yapi-assistant/docs")

val syncKnowledgeBase by tasks.registering {
    group = "documentation"
    description = "Sync docs/knowledge-base/*.md into the plugin resources and the easy-yapi-assistant skill folder."

    // Re-run whenever any source doc changes (Gradle up-to-date checks).
    val sourceFiles = fileTree(knowledgeBaseSourceDir) { include("*.md") }
    inputs.files(sourceFiles)
    // Declared outputs so the task is considered up-to-date when inputs are
    // unchanged — keeping the destinations git-clean.
    outputs.files(fileTree(knowledgeBaseResourceDir) { include("*.md") })
    outputs.files(fileTree(knowledgeBaseSkillDir) { include("*.md") })
    outputs.upToDateWhen { true }

    // Resolve destination directories once, at configuration time.
    val pluginDestDir: File = knowledgeBaseResourceDir
    val skillDestDir: File = knowledgeBaseSkillDir

    doLast {
        val sources = sourceFiles.files.sortedBy { it.name }
        logger.lifecycle("Syncing ${sources.size} knowledge-base doc(s) to plugin resources and skill folder:")
        sources.forEach { source ->
            val name = source.name
            // Plugin resources: every knowledge-base doc ships in the JAR
            // (the get_plugin_doc tool exposes overview/index/rule-guide/
            // settings-guide/usage-guide/easyapi-script-reference).
            copyFileIfDifferent(source, File(pluginDestDir, name))
            // Skill folder: all canonical knowledge-base pages are bundled so
            // the external skill mirrors the built-in agent's `get_plugin_doc`
            // surface as closely as possible (works after `npx skills add`,
            // which publishes only the skills/ folder). They live under a
            // `docs/` subfolder to keep the skill root tidy.
            copyFileIfDifferent(source, File(skillDestDir, name))
            logger.lifecycle("  - $name")
        }
    }
}

/** Copies only when content differs, so an unchanged source stays git-clean. */
fun copyFileIfDifferent(source: File, target: File) {
    target.parentFile.mkdirs()
    if (target.isFile && target.readText() == source.readText()) {
        return
    }
    source.copyTo(target, overwrite = true)
}

// Ensure the JAR always ships docs synced from the canonical source.
tasks.named("processResources") {
    dependsOn("syncKnowledgeBase")
}
