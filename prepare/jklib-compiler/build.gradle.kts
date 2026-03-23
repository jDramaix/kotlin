import java.util.regex.Pattern.quote

plugins {
    kotlin("jvm")
}

val fatJarContents by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
val fatJarContentsStripServices by configurations.registering

val libraries by configurations.registering {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
}

val compilerVersion by configurations.registering
val buildNumber by configurations.registering

val distDir: String by rootProject.extra

dependencies {
    api(commonDependency("org.jetbrains.kotlin:kotlin-reflect")) { isTransitive = false }
    libraries(project(":kotlin-reflect"))

    compilerVersion(project(":compiler:compiler.version"))
    fatJarContents(project(":compiler:cli-jklib")) {
        exclude(group = "com.fasterxml")
        exclude(group = "org.codehaus.woodstox")
    }
    fatJarContents(intellijCore())
    fatJarContents(libs.intellij.fastutil)
    fatJarContentsStripServices(commonDependency("com.fasterxml:aalto-xml")) { isTransitive = false }
    fatJarContents(commonDependency("org.codehaus.woodstox:stax2-api")) { isTransitive = false }

    buildNumber(project(":prepare:build.version", configuration = "buildVersion"))
}

val distSbomTask = configureSbom(
    target = "Dist",
    documentName = "Kotlin Compiler Distribution",
    setOf(
        configurations.runtimeClasspath.name,
        libraries.name,
        fatJarContents.name, fatJarContentsStripServices.name,
    )
)

val jar = runtimeJar {
    archiveFileName.set("kotlin-jklib-compiler.jar")
    dependsOn(fatJarContents, fatJarContentsStripServices, compilerVersion)
    from {
        fatJarContents.get().map(::zipTree)
    }
    from {
        fatJarContentsStripServices.get().map { file ->
            zipTree(file).matching { exclude("META-INF/services/**") }
        }
    }
    from {
        compilerVersion.get().map(::zipTree)
    }

    manifest.attributes["Class-Path"] = compilerManifestClassPath
    manifest.attributes["Main-Class"] = "org.jetbrains.kotlin.cli.jklib.K2JKlibCompiler"
}

val distJklib = distTask<Sync>("distJklib") {
    destinationDir = File("$distDir/jklib")

    from(buildNumber)

    into("license") {
        from("$rootDir/license") 
    }

    into("lib") {
        from(jar)
        from(libraries)
        filePermissions {
            unix("rw-r--r--")
        }
    }
}

val dist = distTask<Copy>("dist") {
    destinationDir = File(distDir)

    dependsOn(distJklib)
    dependsOn(distSbomTask)

    from(buildNumber)
    from(distSbomTask) {
        rename(".*", "${project.name}-${project.version}.spdx.json")
    }
}


inline fun <reified T : AbstractCopyTask> Project.distTask(
    name: String,
    crossinline block: T.() -> Unit
) = tasks.register<T>(name) {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    rename(quote("-$version"), "")
    rename(quote("-$bootstrapKotlinVersion"), "")
    block()
}

artifacts {
    val distElements = configurations.register("distElements")
    add(distElements.name, dist)
}