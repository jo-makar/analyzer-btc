plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.github.jo-makar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.novacrypto:Base58:2022.01.17@jar")
    implementation("org.apache.kafka:kafka-streams:3.9.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.github.jo_makar.MainKt"
    }
}

kotlin {
    jvmToolchain(21)
}
