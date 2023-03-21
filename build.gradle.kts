import java.text.SimpleDateFormat
import java.util.*

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

fun majorVersion(version: String): String {
    val parts = version.split(".")
    return "${parts[0]}.${parts[1]}.${parts[2]}"
}

fun nextVersion(version: String): String {
    val parts = version.split(".")
    val next = ("${parts[0]}${parts[1]}${parts[2]}".toInt() + 1).toString()
    return "${next[0]}.${next[1]}.${next[2]}"
}

val patchUpdate by tasks.registering(Task::class) {
    doLast {
        val majorVersion = majorVersion(version.toString())
        println("current version:$majorVersion")
        val nextVersion = nextVersion(version.toString())
        val nextVersionFull = "$nextVersion.191.0"
        println("next version:$nextVersion")

        val commitsFile = File("plugin-script/commits.txt")
        val commits = commitsFile.readLines().mapNotNull { line ->
            try {
                val matchValues = Regex("^(.*?)\\s*\\(#(\\d+)\\)$").find(line)!!.groupValues
                matchValues[1] to matchValues[2]
            } catch (ignore: Throwable) {
                null
            }
        }

        //patch IDEA_CHANGELOG.md
        val changelogFile = File("IDEA_CHANGELOG.md")
        val oldChangelogFile = File("IDEA_CHANGELOG.md.old")
        changelogFile.renameTo(oldChangelogFile)

        changelogFile.outputStream().use {
            val versionNote = "* ${nextVersion}\n\n"
            it.write(versionNote.encodeToByteArray())
            for ((msg, id) in commits) {
                it.write(
                    "\t* $msg [(#${id})](https://github.com/tangcent/easy-yapi/pull/${id})\n\n".encodeToByteArray()
                )
            }
            val oldFile = oldChangelogFile.readText()
            if (oldFile.startsWith(versionNote)) {
                it.write(oldFile.substring(versionNote.length).encodeToByteArray())
            } else {
                it.write(oldFile.encodeToByteArray())
            }
        }
        oldChangelogFile.delete()

        //patch pluginChanges.html
        val pluginChangesFile = File("idea-plugin/parts/pluginChanges.html")
        pluginChangesFile.delete()
        pluginChangesFile.createNewFile()
        pluginChangesFile.outputStream().use {
            it.write(
                "<a href=\"https://github.com/tangcent/easy-yapi/releases/tag/v${nextVersion}\">v${nextVersion}.191.0(${
                    SimpleDateFormat("yyyy-MM-dd").format(Date())
                })</a>\n".encodeToByteArray()
            )
            it.write("<br/>\n".encodeToByteArray())
            it.write(
                "<a href=\"https://github.com/tangcent/easy-yapi/blob/master/IDEA_CHANGELOG.md\">Full Changelog</a>\n".encodeToByteArray()
            )
            val enhancements = ArrayList<String>()
            val fixes = ArrayList<String>()
            for ((msg, id) in commits) {
                val li = "\t<li> $msg <a\n" +
                        "\t\t\thref=\"https://github.com/tangcent/easy-yapi/pull/${id}\">(#${id})</a>\n" +
                        "\t</li>"
                if (msg.startsWith("fix")) {
                    fixes.add(li)
                } else if (msg.startsWith("feat") || msg.startsWith("opti") || msg.startsWith("perf")) {
                    enhancements.add(li)
                }
            }
            if (enhancements.isNotEmpty()) {
                it.write("<ul>enhancement:\n".encodeToByteArray())
                for (enhancement in enhancements) {
                    it.write("${enhancement}\n".encodeToByteArray())
                }
                it.write("</ul>\n".encodeToByteArray())
            }
            if (fixes.isNotEmpty()) {
                it.write("<ul>fix:\n".encodeToByteArray())
                for (fix in fixes) {
                    it.write("${fix}\n".encodeToByteArray())
                }
                it.write("</ul>\n".encodeToByteArray())
            }
        }

        //update files
        arrayOf("build.gradle.kts", "gradle.properties", "idea-plugin/src/main/resources/META-INF/plugin.xml")
            .map { File(it) }.forEach {
                it.writeText(it.readText().replace(version.toString(), nextVersionFull))
            }

        //set env
        val envDir = File("envs")
        if (envDir.exists()) {
            envDir.delete()
        }
        envDir.mkdir()
        File("envs/EASY_YAPI_PR_TITLE").outputStream().use {
            it.write("release v${nextVersion}".encodeToByteArray())
        }

        var prBody = ""
        for ((msg, id) in commits) {
            prBody += "* $msg [(#${id})](https://github.com/tangcent/easy-yapi/pull/${id})\n\n"
        }
        File("envs/EASY_YAPI_PR_BODY").outputStream().use {
            it.write(prBody.encodeToByteArray())
        }
    }
}
