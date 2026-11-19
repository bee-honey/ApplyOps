plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

val temporalVersion = "1.41.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("io.temporal:temporal-spring-boot-starter:$temporalVersion")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.temporal:temporal-testing:$temporalVersion")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("applyops.schemas.dir", rootProject.file("schemas").absolutePath)
}

// Only produce the executable boot jar (keeps the Docker COPY unambiguous).
tasks.named<Jar>("jar") { enabled = false }
