group = "com.itangcent"
version = properties["plugin_version"]!!

repositories {

    mavenCentral()

//    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib-jdk8
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${properties["kotlin_version"]}")

    implementation("org.jetbrains.kotlin:kotlin-reflect:${properties["kotlin_version"]}")

    implementation("org.jetbrains:markdown:0.4.0")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:${properties["junit_version"]}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${properties["junit_version"]}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${properties["junit_version"]}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${properties["junit_version"]}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${properties["junit_version"]}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${properties["kotlin_version"]}")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}