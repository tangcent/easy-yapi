plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("org.jetbrains.changelog")
    id("com.google.protobuf") version "0.9.4"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.itangcent"
version = "3.1.8.252.0"

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
