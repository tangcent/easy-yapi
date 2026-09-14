plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.changelog")
    id("com.google.protobuf") version "0.9.4"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.itangcent"

// IDEA compatibility range. Default lower bound lives in gradle.properties
// (pluginSinceBuild); override per invocation with -PpluginSinceBuild /
// -PpluginUntilBuild. untilBuild is unbounded when unset, blank, or "*".
val pluginSinceBuild = providers.gradleProperty("pluginSinceBuild").orElse("252")
val pluginUntilBuild = providers.gradleProperty("pluginUntilBuild").orNull
    ?.takeUnless { it.isBlank() || it == "*" }

// Version embeds the range: <base>.<since>.<until|0>, where "0" means unbounded.
// e.g. 3.2.3.252.0 (since 252, unbounded) / 3.2.3.243.252 (243..252) — keeps
// differently-ranged artifacts from overwriting each other in plugin/.
// pluginBaseVersion lives in gradle.properties and is bumped by script/release.sh.
val pluginBaseVersion = providers.gradleProperty("pluginBaseVersion").get()
version = "$pluginBaseVersion.${pluginSinceBuild.get()}.${pluginUntilBuild ?: "0"}"

changelog {
    // version is "3.2.3.<since>.<until>"; changelog uses semver "3.2.3"
    version.set(project.version.toString().split(".").take(3).joinToString("."))
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
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.idea.maven",
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.kotlin",
            "org.intellij.groovy",
            "org.intellij.intelliLang"
        )
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
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.2")
    implementation("org.yaml:snakeyaml:1.33")
    implementation("org.xerial:sqlite-jdbc:3.34.0")

    // LangChain4j — AI agent substrate
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
    testImplementation("io.kotest:kotest-property-jvm:5.9.1") {
        // Use the IntelliJ test runtime's compatible Kotlin and coroutines libraries.
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }
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

// Build with whatever JDK (17 or 21) the developer has installed — no pinned
// toolchain. The jvmTarget / sourceCompatibility / targetCompatibility below
// keep the bytecode JDK 17 compatible regardless of which JDK runs the build.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
}

// Guard against accidental use of JDK 21-only APIs when building with JDK 21
// but targeting JDK 17 bytecode (e.g. protobuf-generated Java sources).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    maxParallelForks = 1
    // The forked test JVM loads the full IntelliJ Platform + bundled plugins
    // (Java, Kotlin, Groovy, Scala), which is memory-hungry. The default heap
    // is far too small and leads to OOM-induced stub-index corruption
    // (`Stubs` index status REQUIRES_REBUILD), which in turn fails tests
    // non-deterministically. 3g gives the index/cache layer enough headroom
    // for the full suite (5000+ tests) to run without OOM.
    maxHeapSize = "3g"
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
            sinceBuild = pluginSinceBuild.get()
            untilBuild = provider { pluginUntilBuild }
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
            // settings-guide/usage-guide/postman-script-reference).
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

// ─────────────────────────────────────────────────────────────────────────────
// Agent catalog sync (R3-C3)
//
// `src/main/resources/ai/{detection,key-guides}/*.md` are the canonical prompt
// catalog the in-plugin agent loads at runtime via `PromptCatalog`. The
// external `easy-yapi-assistant` skill ships a verbatim copy under
// `skills/easy-yapi-assistant/ai/` so the external assistant has the same
// per-detection / per-key guide surface (mirrored by the
// `get_detection_prompt.sh` / `get_key_guide.sh` CLI scripts).
//
// This task is the catalog counterpart of `syncKnowledgeBase` — same
// content-equality-checked / idempotent contract. It does NOT sync
// `agent-base.md` or `catalog-manifest.txt`: those are in-plugin prompt
// infrastructure, not part of the external skill's surface (the external
// assistant reads the catalog via scripts, not via `agent-base.md`).
// ─────────────────────────────────────────────────────────────────────────────
val agentCatalogSourceDir = file("src/main/resources/ai")
val agentCatalogSkillDir = file("skills/easy-yapi-assistant/ai")

val syncAgentCatalog by tasks.registering {
    group = "documentation"
    description = "Sync src/main/resources/ai/{detection,key-guides}/*.md into the easy-yapi-assistant skill folder."

    val sourceFiles = fileTree(agentCatalogSourceDir) {
        include("detection/*.md")
        include("key-guides/*.md")
    }
    inputs.files(sourceFiles)
    outputs.files(fileTree(agentCatalogSkillDir) { include("**/*.md") })
    outputs.upToDateWhen { true }

    doLast {
        val sources = sourceFiles.files.sortedBy { it.path }
        logger.lifecycle("Syncing ${sources.size} agent-catalog file(s) into the easy-yapi-assistant skill folder:")
        sources.forEach { source ->
            val rel = source.relativeTo(agentCatalogSourceDir).path   // detection/<id>.md / key-guides/<key>.md
            copyFileIfDifferent(source, File(agentCatalogSkillDir, rel))
            logger.lifecycle("  - $rel")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rule-key scheme catalog sync (auto-export)
//
// `RuleKeySchemeExporter` reflects every `*RuleKeys` object (general, channel,
// framework) plus the implicit keys in `RuleKeyRegistry` and emits the FULL
// self-describing scheme of each key — the same information the in-plugin
// `list_rule_keys` tool exposes to the built-in agent. The external
// `easy-yapi-assistant` skill ships these two generated files under
// `skills/easy-yapi-assistant/rule-keys.json|.md` so it gets scheme-equivalent
// coverage of every rule key, present and future.
//
// Because each source is enumerated by reflection, any new key added to an
// existing `*RuleKeys` object is picked up automatically on re-run. A brand
// new source object (a new channel/framework) must be registered in
// `RuleKeyCatalog.SOURCES` — the guard tests
// (`RuleKeySchemeExporterTest`, `EasyApiAssistantSkillTest`) fail if a
// `RuleKeyRegistry` source is missing there.
// ─────────────────────────────────────────────────────────────────────────────
val ruleKeySchemeSkillDir = file("skills/easy-yapi-assistant")

val syncRuleKeySchemes by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Reflect every *RuleKeys object + implicit keys and write the full scheme catalog into the easy-yapi-assistant skill (rule-keys.json / rule-keys.md)."

    dependsOn("classes", "testClasses")
    // intellijPlatform supplies Kotlin stdlib via the IDE at runtime, not on
    // main's runtimeClasspath — add it explicitly for the plain JVM run.
    val kotlinRuntime = configurations.detachedConfiguration(
        dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:2.1.0"),
        // RuleKey.collectFrom uses kotlin-reflect's memberProperties
        dependencies.create("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    )
    // The exporters live in the test source set (build-time tools, not shipped
    // in the plugin JAR), so the exec runs off the test output + test classpath.
    classpath = sourceSets.test.get().output + sourceSets.test.get().runtimeClasspath + kotlinRuntime
    mainClass.set("com.itangcent.easyapi.tooling.RuleKeySchemeExporter")
    args(ruleKeySchemeSkillDir.absolutePath)

    // Re-run whenever the exporter or any rule-key source object changes.
    inputs.file(file("src/test/kotlin/com/itangcent/easyapi/tooling/RuleKeySchemeExporter.kt"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/core/rule"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/channel"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/framework"))
    outputs.files(fileTree(ruleKeySchemeSkillDir) { include("rule-keys.json", "rule-keys.md") })
}

// ─────────────────────────────────────────────────────────────────────────────
// Rule-key script-context catalog sync (get_rule_context mirror)
//
// `RuleContextExporter` reuses the same key assembly as `syncRuleKeySchemes`
// and, for every rule key, reflects the runtime script-context profile that the
// in-plugin `get_rule_context` tool returns to the built-in agent: execution
// mode, per-key bindings, and the callable script-object method signatures
// (`it`/`request`/`response`/`api`/…). The external `easy-yapi-assistant` skill
// ships these as `rule-contexts.json` + `rule-contexts.md` (read by
// `scripts/get_key_context.sh`), so it authors scripts against the same real,
// reflected object API the built-in agent sees — covering rule keys and the
// implicit keys read by name, through one interface.
//
// This is a runtime-reflective pass (needs the IntelliJ classes on the
// classpath to load the script wrapper types), so it runs standalone — not wired
// into `processResources`.
// ─────────────────────────────────────────────────────────────────────────────
val syncRuleContexts by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Reflect each rule key's runtime script-context and write rule-contexts.json / rule-contexts.md into the easy-yapi-assistant skill."

    dependsOn("classes", "testClasses")
    val kotlinRuntime = configurations.detachedConfiguration(
        dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:2.1.0"),
        dependencies.create("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    )
    // The script-context profiler loads IntelliJ/PSI script-wrapper types, so
    // the exec needs the IDE platform — compileClasspath carries it. (RuleKeySchemeExporter
    // avoids IntelliJ types and runs off runtimeClasspath alone.) The exporters
    // live in the test source set, so run off the test output + test classpath.
    classpath = sourceSets.test.get().output + sourceSets.test.get().compileClasspath + kotlinRuntime
    mainClass.set("com.itangcent.easyapi.tooling.RuleContextExporter")
    args(ruleKeySchemeSkillDir)

    inputs.file(file("src/test/kotlin/com/itangcent/easyapi/tooling/RuleKeySchemeExporter.kt"))
    inputs.file(file("src/test/kotlin/com/itangcent/easyapi/tooling/RuleContextExporter.kt"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/core/rule"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/channel"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/framework"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/core/http"))
    outputs.files(fileTree(ruleKeySchemeSkillDir) { include("rule-contexts.json", "rule-contexts.md") })
}

// ─────────────────────────────────────────────────────────────────────────────
// Code-derived fact sheets (tools / locales / extension points)
//
// `SkillFactsExporter` writes three small generated references into the skill:
// `tools.md` (the AI-tool inventory read from the three registries in
// `core/ai/tools/RuleTools.kt`), `locales.md` (the bundled Markdown-template
// locales), and `extensions.md` (the `plugin.xml` EP declarations + registered
// implementations). They replace hand-maintained enumerations in SKILL.md /
// rule-guide.md that could drift silently.
//
// Reflects main classes (tools + BundledLanguageTemplates), so it runs off the
// test output + test compile classpath — like `syncRuleContexts`, standalone,
// not on `processResources`.
// ─────────────────────────────────────────────────────────────────────────────
val syncSkillFacts by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "Generate tools.md / locales.md / extensions.md into the easy-yapi-assistant skill from the tool registries, the locale registry, and plugin.xml."

    dependsOn("classes", "testClasses")
    val kotlinRuntime = configurations.detachedConfiguration(
        dependencies.create("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    )
    classpath = sourceSets.test.get().output + sourceSets.test.get().compileClasspath + kotlinRuntime
    mainClass.set("com.itangcent.easyapi.tooling.SkillFactsExporter")
    args(ruleKeySchemeSkillDir.absolutePath)

    inputs.file(file("src/test/kotlin/com/itangcent/easyapi/tooling/SkillFactsExporter.kt"))
    inputs.file(file("src/main/resources/META-INF/plugin.xml"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/core/ai/tools"))
    inputs.dir(file("src/main/kotlin/com/itangcent/easyapi/channel/markdown/template"))
    inputs.dir(file("skills/easy-yapi-assistant/scripts"))
    outputs.files(fileTree(ruleKeySchemeSkillDir) { include("tools.md", "locales.md", "extensions.md") })
}

// Ensure the JAR always ships docs synced from the canonical source.
// `syncRuleKeySchemes` is intentionally NOT wired into the build: it depends on
// `classes` (to run the exporter) and only needs to re-run before committing a
// change to any rule-key source (like the git-tracked docs/knowledge-base copy).
tasks.named("processResources") {
    dependsOn("syncKnowledgeBase")
    dependsOn("syncAgentCatalog")
}

// ─────────────────────────────────────────────────────────────────────────────
// Aggregated skill sync (D4.4)
//
// One command to refresh the entire `skills/easy-yapi-assistant` mirror: the
// knowledge base, the agent catalog (detection + key-guides), the rule-key
// scheme catalog, the rule script-context catalog, and the code-derived fact
// sheets (tools / locales / extensions). The exporters depend on `classes`
// (they run reflectively), so they are kept out of `processResources` and run
// here on demand — trigger them by hand (or let the content-guard tests fail)
// before committing any rule-key / scheme / renderer change.
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("syncSkill") {
    group = "documentation"
    description = "Refresh the entire easy-yapi-assistant skill mirror (knowledge base, agent catalog, rule-key schemes, rule contexts, fact sheets)."
    dependsOn(
        "syncKnowledgeBase", "syncAgentCatalog", "syncRuleKeySchemes", "syncRuleContexts", "syncSkillFacts"
    )
}
