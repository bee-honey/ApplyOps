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
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20261106-2.9.2")
    implementation("com.google.api-client:google-api-client:2.9.2")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.temporal:temporal-testing:$temporalVersion")
    testImplementation("com.networknt:json-schema-validator:1.5.9")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("applyops.schemas.dir", rootProject.file("schemas").absolutePath)
}

springBoot {
    mainClass.set("dev.applyops.orchestrator.OrchestratorApplication")
}

// One-time Gmail OAuth consent on the host (opens a browser). See Makefile: make gmail-auth
tasks.register<JavaExec>("gmailAuth") {
    group = "applyops"
    description = "Authorize read-only Gmail access and store the token under data/"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.applyops.orchestrator.discovery.gmail.GmailAuthCli")
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.register<JavaExec>("gmailDump") {
    group = "applyops"
    description = "Dev: save raw alert emails under data/alert-samples (args: query, max)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.applyops.orchestrator.discovery.gmail.GmailDumpCli")
    workingDir = rootProject.projectDir
    args = listOf(providers.gradleProperty("q").getOrElse(""), providers.gradleProperty("n").getOrElse("10"))
}

// Only produce the executable boot jar (keeps the Docker COPY unambiguous).
tasks.named<Jar>("jar") { enabled = false }
tasks.withType<JavaCompile> { options.compilerArgs.add("-Xlint:deprecation") }
