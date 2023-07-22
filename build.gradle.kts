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
    val javaVersion = JavaVersion.current()
    if (!javaVersion.isJava11Compatible) {
        throw Error("incompatible jdk version: $javaVersion")
    }
    val majorVersion = JavaVersion.current().majorVersion.toInt()
    println("use jvmToolchain: $majorVersion")
    jvmToolchain(majorVersion)
}

tasks.create("codeCoverageReport", JacocoReport::class) {
    executionData(
        fileTree(project.rootDir.absolutePath).include("**/build/jacoco/*.exec")
    )

    val exclusiveDirectories = listOf("**/common/model/**")

    subprojects.forEach { project ->
        sourceDirectories.from(project.files("src/main/kotlin").map {
            fileTree(it).matching {
                exclude(exclusiveDirectories)
            }
        })
        classDirectories.from(project.files("build/classes/kotlin/main").map {
            fileTree(it).matching {
                exclude(exclusiveDirectories)
            }
        })
    }

    reports {
        xml.required.set(true)
        xml.outputLocation.set(file("${buildDir}/reports/jacoco/report.xml").apply { parentFile.mkdirs() })
        html.required.set(false)
        csv.required.set(false)
    }

    generate()
}