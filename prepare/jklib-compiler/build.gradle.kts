// --- START TEMPORARY: KEEP ---
import org.gradle.internal.jvm.Jvm 
import java.util.regex.Pattern.quote

description = "JKlib Compiler"


plugins {
    // HACK: java plugin makes idea import dependencies on this project as source (with empty sources however),
    // this prevents reindexing of kotlin-compiler.jar after build on every change in compiler modules
    `java-library`
    // required to disambiguate attributes of non-jvm Kotlin libraries
    kotlin("jvm") 
}

val fatJarContents by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
val fatJarContentsStripMetadata by configurations.creating
val fatJarContentsStripServices by configurations.creating
val fatJarContentsStripVersions by configurations.creating

val compilerVersion by configurations.creating

val builtinsMetadata by configurations.creating

val api by configurations
val proguardLibraries by configurations.creating {
    extendsFrom(api)
}
val buildNumber by configurations.creating

val compilerBaseName = name

val compilerModules: Array<String> by rootProject.extra

configurations.all {
    resolutionStrategy {
        preferProjectModules()
    }
}

dependencies {
    api(kotlinStdlib("jdk8"))
    api(project(":kotlin-script-runtime"))
    api(commonDependency("org.jetbrains.kotlin:kotlin-reflect")) { isTransitive = false }
    api(libs.kotlinx.coroutines.core)
    api(project(":compiler:build-tools:kotlin-build-tools-api"))

    proguardLibraries(project(":kotlin-annotations-jvm"))

    compilerVersion(project(":compiler:compiler.version"))
    proguardLibraries(project(":compiler:compiler.version"))

    compilerModules
        .filter { it != ":compiler:compiler.version" } // Version will be added directly to the final jar excluding proguard and relocation
        .forEach {
            fatJarContents(project(it)) { isTransitive = false }
        }

    // Jklib modules
    fatJarContents(project(":compiler:cli-jklib")) { isTransitive = false }
    fatJarContents(project(":compiler:ir.serialization.jklib")) { isTransitive = false }

    buildNumber(project(":prepare:build.version", configuration = "buildVersion"))

    fatJarContents(commonDependency("javax.inject"))
    fatJarContents(commonDependency("org.jline", "jline"))
    fatJarContents(commonDependency("org.fusesource.jansi", "jansi"))
    fatJarContents(protobufFull())
    fatJarContents(commonDependency("com.google.code.findbugs", "jsr305"))
    fatJarContents(libs.vavr)
    fatJarContents(commonDependency("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm")) { isTransitive = false }

    fatJarContents(intellijCore())
    fatJarContents(commonDependency("org.jetbrains.intellij.deps.jna:jna")) { isTransitive = false }
    fatJarContents(commonDependency("org.jetbrains.intellij.deps.jna:jna-platform")) { isTransitive = false }
    fatJarContents(libs.intellij.fastutil)
    fatJarContents(commonDependency("org.lz4:lz4-java")) { isTransitive = false }
    fatJarContents(libs.intellij.asm) { isTransitive = false }
    fatJarContents(libs.guava) { isTransitive = false }
    fatJarContents(libs.guava.failureaccess) { isTransitive = false }
    //Gson is needed for kotlin-build-statistics. Build statistics could be enabled for JPS and Gradle builds. Gson will come from inteliij or KGP.
    proguardLibraries(commonDependency("com.google.code.gson:gson")) { isTransitive = false }

    fatJarContentsStripServices(commonDependency("com.fasterxml:aalto-xml")) { isTransitive = false }
    fatJarContents(commonDependency("org.codehaus.woodstox:stax2-api")) { isTransitive = false }

    fatJarContentsStripMetadata(commonDependency("oro:oro")) { isTransitive = false }
    fatJarContentsStripMetadata(intellijJDom()) { isTransitive = false }
    fatJarContentsStripMetadata(commonDependency("org.jetbrains.intellij.deps:log4j")) { isTransitive = false }
    fatJarContentsStripVersions(commonDependency("one.util:streamex")) { isTransitive = false }

    // Used by JS parser
    fatJarContents(libs.antlr.runtime) { isTransitive = false }
    proguardLibraries(libs.antlr.runtime) { isTransitive = false }

    builtinsMetadata(kotlinStdlib())
    // --- END: kotlin-compiler.jar external libraries ---
}

val distSbomTask = configureSbom(
    target = "Dist",
    documentName = "Kotlin Compiler Distribution",
    setOf(
        configurations.runtimeClasspath.name,
        fatJarContents.name, fatJarContentsStripServices.name, fatJarContentsStripMetadata.name, fatJarContentsStripVersions.name,
        proguardLibraries.name,
    )
) 

val packCompiler by task<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveClassifier.set("before-proguard")

    dependsOn(fatJarContents)
    from {
        fatJarContents.map(::zipTree)
    }

    dependsOn(fatJarContentsStripServices)
    from {
        fatJarContentsStripServices.files.map {
            zipTree(it).matching { exclude("META-INF/services/**") }
        }
    }

    dependsOn(fatJarContentsStripMetadata)
    from {
        fatJarContentsStripMetadata.files.map {
            zipTree(it).matching { exclude("META-INF/jb/**", "META-INF/LICENSE") }
        }
    }

    dependsOn(fatJarContentsStripVersions)
    from {
        fatJarContentsStripVersions.files.map {
            zipTree(it).matching {
                includeEmptyDirs = false
                exclude("META-INF/versions/**")
            }
        }
    }

    dependsOn(builtinsMetadata)
    from {
        builtinsMetadata.files.map {
            zipTree(it).matching { include("**/*.kotlin_builtins") }
        }
    }
}

val proguard by task<CacheableProguardTask> {
    dependsOn(packCompiler)

    javaLauncher.set(project.getToolchainLauncherFor(JdkMajorVersion.JDK_1_8))

    configuration(layout.projectDirectory.file("compiler.pro"))

    injars(
        mapOf("filter" to """
            !org/apache/log4j/jmx/Agent*,
            !org/apache/log4j/net/JMS*,
            !org/apache/log4j/net/SMTP*,
            !org/apache/log4j/or/jms/MessageRenderer*,
            !org/jdom/xpath/Jaxen*,
            !org/jline/builtins/ssh/**,
            !org/mozilla/javascript/xml/impl/xmlbeans/**,
            !net/sf/cglib/**,
            !META-INF/maven**,
            **.class,**.properties,**.kt,**.kotlin_*,**.jnilib,**.so,**.dll,**.txt,**.caps,
            custom-formatters.js,
            META-INF/services/**,META-INF/native/**,META-INF/extensions/**,META-INF/MANIFEST.MF,
            messages/**""".trimIndent()),
        packCompiler.map { it.outputs.files.singleFile }
    )

    outjars(layout.buildDirectory.file("libs/$compilerBaseName-after-proguard.jar"))

    libraryjars(mapOf("filter" to "!META-INF/versions/**"), proguardLibraries)
    libraryjars(
        files(
            javaLauncher.map {
                firstFromJavaHomeThatExists(
                    "jre/lib/rt.jar",
                    "../Classes/classes.jar",
                    jdkHome = it.metadata.installationPath.asFile
                )!!
            },
            javaLauncher.map {
                firstFromJavaHomeThatExists(
                    "jre/lib/jsse.jar",
                    "../Classes/jsse.jar",
                    jdkHome = it.metadata.installationPath.asFile
                )!!
            },
            javaLauncher.map {
                Jvm.forHome(it.metadata.installationPath.asFile).toolsJar!!
            }
        )
    )

    printconfiguration(layout.buildDirectory.file("compiler.pro.dump"))
}

val pack: TaskProvider<out DefaultTask> = if (kotlinBuildProperties.proguard) proguard else packCompiler
val distDir: String by rootProject.extra

val jar = runtimeJar {
    dependsOn(pack)
    dependsOn(compilerVersion)

    from {
        pack.map { zipTree(it.singleOutputFile(layout)) }
    }

    from {
        compilerVersion.map(::zipTree)
    }

    manifest.attributes["Class-Path"] = compilerManifestClassPath
    manifest.attributes["Main-Class"] = "org.jetbrains.kotlin.cli.jklib.K2JKlibCompiler"
}

val distKotlinc = distTask<Sync>("distKotlinc") {
    destinationDir = File("$distDir/jklib")

    from(buildNumber)

    val licenseFiles = files("$rootDir/license")
    into("license") {
        from(licenseFiles)
    }

    val compilerBaseName = compilerBaseName
    val jarFiles = files(jar)
    into("lib") {
        from(jarFiles) { rename { "$compilerBaseName.jar" } }
        filePermissions {
            unix("rw-r--r--")
        }
    }
}

val dist = distTask<Copy>("dist") {
    destinationDir = File(distDir)

    dependsOn(distKotlinc)
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
    val distElements = configurations.create("distElements")
    add(distElements.name, dist)
}