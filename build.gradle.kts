plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    id("application")
    id("jacoco")
    `java-library`
}

subprojects {
    plugins.apply("java")
    plugins.apply("kotlin")
    plugins.apply("jacoco")

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

group = "com.itangcent"
version = properties["plugin_version"]!!

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.create("codeCoverageReport", JacocoReport::class) {
    executionData(
        fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec")
    )
    subprojects.forEach {
        sourceDirectories.from(it.file("src/main/kotlin"))
        classDirectories.from(it.file("build/classes/kotlin/main"))
    }
    reports {
        xml.required.set(true)
        xml.outputLocation.set(file("${buildDir}/reports/jacoco/report.xml").apply { parentFile.mkdirs() })
        html.required.set(false)
        csv.required.set(false)
    }

    generate()
}