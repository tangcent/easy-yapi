plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
    id("com.google.protobuf") version "0.9.4"
    jacoco
}

group = "com.itangcent"
version = "3.0.8.252.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.1")
        bundledPlugins("com.intellij.java", "org.jetbrains.idea.maven", "org.jetbrains.plugins.gradle")
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
    // Ensure JaCoCo agent is attached to the forked test JVM
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.itangcent.idea.plugin.easy-yapi"
        name = "EasyYapi"
        version = project.version.toString()
        description = file("src/main/resources/pluginDescription.html").readText()
        changeNotes = provider {
            val lines = file("CHANGELOG.md").readLines()
            val start = lines.indexOfFirst { it.startsWith("## [") }
            val end = lines.drop(start + 1).indexOfFirst { it.startsWith("## [") }
            val section = if (end >= 0) lines.subList(start, start + 1 + end) else lines.drop(start)
            section.joinToString("<br/>")
                .replace("### ", "<h3>")
                .replace("<br/>- ", "<br/>• ")
        }
        ideaVersion {
            sinceBuild = "252"
            untilBuild = provider { null }
        }
    }

    sandboxContainer = layout.projectDirectory.dir("idea-sandbox")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main"))
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/*.exec") }
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/report.xml"))
        html.required.set(true)
    }
}
