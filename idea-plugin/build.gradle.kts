plugins {
    id("org.jetbrains.intellij") version "1.13.2"
}

group = "com.itangcent"
version = properties["plugin_version"]!!

repositories {
    mavenCentral()
}

dependencies {

    implementation(project(":common-api")) {
        exclude("org.apache.httpcomponents", "httpclient")
    }
    implementation(project(":plugin-adapter:plugin-adapter-markdown"))


    implementation("com.itangcent:intellij-idea:${properties["itangcent_intellij_version"]}") {
        exclude("com.google.inject")
        exclude("com.google.code.gson")
    }

    implementation("com.itangcent:intellij-kotlin-support:${properties["itangcent_intellij_version"]}") {
        exclude("com.google.inject")
        exclude("com.google.code.gson")
    }

    implementation("com.itangcent:intellij-groovy-support:${properties["itangcent_intellij_version"]}") {
        exclude("com.google.inject")
        exclude("com.google.code.gson")
    }

//    implementation("com.itangcent:intellij-scala-support:${properties["itangcent_intellij_version"]}") {
//        exclude("com.google.inject")
//        exclude("com.google.code.gson")
//    }

    implementation("com.google.inject:guice:4.2.2") {
        exclude("org.checkerframework", "checker-compat-qual")
        exclude("com.google.guava", "guava")
    }

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.0")

    // https://mvnrepository.com/artifact/org.jsoup/jsoup
    implementation("org.jsoup:jsoup:1.12.1")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")

    // https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:3.34.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${properties["junit_version"]}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${properties["junit_version"]}")

    // https://search.maven.org/artifact/org.mockito.kotlin/mockito-kotlin/3.2.0/jar
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")

    // https://mvnrepository.com/artifact/org.mockito/mockito-inline
    testImplementation("org.mockito:mockito-inline:3.11.0")

    testImplementation("com.itangcent:intellij-idea-test:${properties["itangcent_intellij_version"]}")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

intellij {
    version.set("2021.2.1")
    type.set("IC")
    pluginName.set("easy-yapi")
    sandboxDir.set("idea-sandbox")
    plugins.set(listOf("java"))
}

tasks {
    patchPluginXml {
        pluginDescription.set(file("parts/pluginDescription.html").readText())
        changeNotes.set(file("parts/pluginChanges.html").readText())

        sinceBuild.set("212")
        untilBuild.set("")
    }
}
