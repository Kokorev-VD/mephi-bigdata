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
}

dependencies {
    compileOnly("org.apache.spark:spark-core_2.12:3.5.0")
    compileOnly("org.apache.spark:spark-sql_2.12:3.5.0")
    implementation("org.apache.hbase:hbase-client:2.5.5")
    implementation("org.apache.hbase:hbase-common:2.5.5")
    implementation("org.apache.hbase:hbase-mapreduce:2.5.5")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.shadowJar {
    archiveFileName.set("network-analyzer.jar")
    manifest {
        attributes["Main-Class"] = "NetworkTrafficAnalyzer"
    }
}
