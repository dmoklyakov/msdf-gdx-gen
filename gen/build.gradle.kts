import proguard.gradle.ProGuardTask
import java.util.*

plugins {
    kotlin("jvm")
    id("com.github.breadmoirai.github-release")
}

val mainClassName = "com.maltaisn.msdfgdx.gen.MainKt"

dependencies {
    val gdxVersion: String by project
    val coroutinesVersion: String by project
    val jcommanderVersion: String by project
    val pngtasticVersion: String by project

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("com.beust:jcommander:$jcommanderVersion")
    implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
    implementation("com.github.depsypher:pngtastic:$pngtasticVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}

tasks.register<JavaExec>("run") {
    main = mainClassName
    classpath = sourceSets.main.get().runtimeClasspath
    standardInput = System.`in`
    isIgnoreExitValue = true

    // Get program test arguments defined in local.properties.
    val properties = Properties()
    properties.load(project.rootProject.file("local.properties").inputStream())
    setArgsString(properties.getProperty("gen-test-args"))

    if ("mac" in System.getProperty("os.name").toLowerCase()) {
        jvmArgs("-XstartOnFirstThread")
    }
}

// Use this task to create a fat jar.
val dist = tasks.register<Jar>("dist") {
    from(files(sourceSets.main.get().output.classesDirs))
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    archiveBaseName.set("msdfgen")

    manifest {
        attributes["Main-Class"] = mainClassName
    }
    finalizedBy(tasks.named("shrinkJar"))
}

tasks.register<ProGuardTask>("shrinkJar") {
    val distFile = dist.get().archiveFile.get().asFile
    configuration("proguard-rules.pro")
    injars(distFile)
    outjars(file("$buildDir/msdfgen.jar"))
    libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
    libraryjars(configurations.runtimeClasspath.get().files)
}

// Publish a new release to Github, using the lastest defined libVersion property,
// a git tag, and the release notes in CHANGELOG.md.
githubRelease {
    val genVersion: String by project

    if (project.hasProperty("githubReleasePluginToken")) {
        val githubReleasePluginToken: String by project
        token(githubReleasePluginToken)
    }
    owner("maltaisn")
    repo("msdf-gdx-gen")

    tagName("v$genVersion")
    targetCommitish("master")
    releaseName("v$genVersion")

    body {
        // Get release notes for version from changelog file.
        val changelog = file("../CHANGELOG.md")
        val versionChanges = StringBuilder()
        var foundVersion = false
        for (line in changelog.readLines()) {
            if (foundVersion && line.matches("""^#+\s*v.+$""".toRegex())) {
                break
            } else if (line.matches("""^#+\s*v$genVersion$""".toRegex())) {
                foundVersion = true
            } else if (foundVersion) {
                versionChanges.append(line)
                versionChanges.append('\n')
            }
        }
        if (!foundVersion) {
            throw GradleException("No release notes for version $genVersion")
        }
        versionChanges.toString().trim()
    }

    releaseAssets("$buildDir/msdfgen.jar")

    overwrite(true)
}
tasks.named("githubRelease") {
    dependsOn("build", "dist")
}
