plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://clojars.org/repo")
    }
}

dependencies {
    compileOnly("org.apache.storm:storm-core:2.6.0")
    implementation("io.nats:jnats:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.shadowJar {
    archiveFileName.set("storm-app.jar")
    manifest {
        attributes["Main-Class"] = "SpikeDetectorTopology"
    }
}
