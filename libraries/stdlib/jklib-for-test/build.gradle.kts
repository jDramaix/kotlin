description = "Kotlin JKlib Stdlib for Tests"

plugins {
    kotlin("jvm")
    base
}

project.configureJvmToolchain(JdkMajorVersion.JDK_1_8)

val stdlibProjectDir = file("$rootDir/libraries/stdlib")

dependencies {
    implementation(project(":compiler:cli-jklib"))
    implementation(commonDependency("org.jetbrains.kotlin:kotlin-reflect")) {
        isTransitive = false
    }
    implementation(intellijCore())
    
    // Transitive dependencies pulled by IntellijCore
    // Used for IR interning and seriliazation and other things
    implementation(libs.intellij.fastutil)
    // Used to read XML metadata files inside META-INF
    implementation(commonDependency("org.codehaus.woodstox:stax2-api"))
    implementation(commonDependency("com.fasterxml:aalto-xml"))
}

val outputKlib = layout.buildDirectory.file("libs/kotlin-stdlib-jvm-ir.klib")

val copyMinimalSources by tasks.registering(Sync::class) {
    dependsOn(":prepare:build.version:writeStdlibVersion")
    into(layout.buildDirectory.dir("src/genesis-minimal"))

    from("src/stubs/jvm/builtins") {
        include("**")
        into("jvm/builtins")
    }

    from("src/stubs") {
        include("kotlin/**")
        include("kotlin/util/**")
        into("common/src")
    }

    from(stdlibProjectDir.resolve("src")) {
        include(
            "kotlin/Annotation.kt",
            "kotlin/Annotations.kt",
            "kotlin/Any.kt",
            "kotlin/Array.kt",
            "kotlin/ArrayIntrinsics.kt",
            "kotlin/Arrays.kt",
            "kotlin/Boolean.kt",
            "kotlin/CharSequence.kt",
            "kotlin/Comparable.kt",
            "kotlin/Enum.kt",
            "kotlin/Function.kt",
            "kotlin/Iterator.kt",
            "kotlin/Library.kt",
            "kotlin/Nothing.kt",
            "kotlin/Number.kt",
            "kotlin/String.kt",
            "kotlin/Throwable.kt",
            "kotlin/Unit.kt",
            "kotlin/annotation/Annotations.kt",
            "kotlin/annotations/Multiplatform.kt",
            "kotlin/annotations/WasExperimental.kt",
            "kotlin/annotations/ReturnValue.kt",
            "kotlin/internal/Annotations.kt",
            "kotlin/internal/AnnotationsBuiltin.kt",
            "kotlin/concurrent/atomics/AtomicArrays.common.kt",
            "kotlin/concurrent/atomics/Atomics.common.kt",
            "kotlin/contextParameters/Context.kt",
            "kotlin/contextParameters/ContextOf.kt",
            "kotlin/contracts/ContractBuilder.kt",
            "kotlin/contracts/Effect.kt",
        )
        into("common/src")
    }
    

    from(stdlibProjectDir.resolve("common/src")) {
        include(
            "kotlin/ExceptionsH.kt",
        )
        into("common/common")
    }

    from(stdlibProjectDir.resolve("jvm/runtime")) {
        include(
            "kotlin/NoWhenBranchMatchedException.kt",
            "kotlin/UninitializedPropertyAccessException.kt",
            "kotlin/TypeAliases.kt",
            "kotlin/text/TypeAliases.kt",
        )
        into("jvm/runtime")
    }
    from(stdlibProjectDir.resolve("jvm/src")) {
        include(
            "kotlin/ArrayIntrinsics.kt",
            "kotlin/Unit.kt",
            "kotlin/collections/TypeAliases.kt",
            "kotlin/enums/EnumEntriesJVM.kt",
            "kotlin/io/Serializable.kt",
        )
        into("jvm/src")
    }
    
    from(stdlibProjectDir.resolve("jvm/builtins")) {
        include("*.kt")
        exclude("Char.kt")
        exclude("Primitives.kt")
        exclude("Collections.kt")
        into("jvm/builtins")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

fun JavaExec.configureJklibCompilation(
    sourceTask: TaskProvider<Sync>,
    klibOutput: Provider<RegularFile>,
    classpathJar: Provider<RegularFile>,
    extraClasspath: FileCollection = project.files()
) {
    dependsOn(sourceTask)
    
    dependsOn(classpathJar)
    
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.jetbrains.kotlin.cli.jklib.K2JKlibCompiler")

    val inputDir = sourceTask.map { it.destinationDir }
    val sourceTree = fileTree(inputDir) {
        include("**/*")
    }
    inputs.files(sourceTree)
    inputs.file(classpathJar)
    outputs.file(klibOutput)

    val runtimeClasspath = project.configurations.getByName("runtimeClasspath")
    val kotlinReflectFileCollection = runtimeClasspath.filter { it.name.startsWith("kotlin-reflect") }
    
    inputs.files(kotlinReflectFileCollection)

    doFirst {
        val allFiles = inputs.files.files.filter { it.extension == "kt" }
        val commonFiles = allFiles.filter { it.path.contains("/common/") }
        val jvmFiles = allFiles.filter { !it.path.contains("/common/") }

        val jvmSourceFiles = jvmFiles.map { it.absolutePath }
        val commonSourceFiles = commonFiles.map { it.absolutePath }

        logger.lifecycle("Compiling ${jvmSourceFiles.size} JVM files and ${commonSourceFiles.size} Common files, total ${allFiles.size}")
        logger.lifecycle("Running K2JKlibCompiler with Java version: ${System.getProperty("java.version")}")

        val outputPath = outputs.files.singleFile.absolutePath

        args(
            "-no-stdlib",
            "-Xallow-kotlin-package",
            "-Xexpect-actual-classes",
            "-module-name", "kotlin-stdlib",
            "-language-version", "2.3",
            "-api-version", "2.3",
            "-Xstdlib-compilation",
            "-d", outputPath,
            "-Xmulti-platform",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.ExperimentalMultiplatform",
            "-opt-in=kotlin.contracts.ExperimentalExtendedContracts",
            "-Xcontext-parameters",
            "-Xcompile-builtins-as-part-of-stdlib",
            "-Xreturn-value-checker=full",
            "-Xcommon-sources=${(commonSourceFiles).joinToString(",")}",
        )
        
        
        val kotlinReflectJar = kotlinReflectFileCollection.singleOrNull()
        val fullClasspath = listOfNotNull(
            classpathJar.get().asFile.absolutePath,
            kotlinReflectJar?.absolutePath,
            extraClasspath.asPath
        ).joinToString(File.pathSeparator)

        args("-classpath", fullClasspath)

        args(jvmSourceFiles)
        args(commonSourceFiles)
    }
}

val fullStdlibJarProvider = project(":kotlin-stdlib").tasks.named("jvmJar", Jar::class).flatMap { it.archiveFile }

val compileStdlib by tasks.registering(JavaExec::class) {
    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
    configureJklibCompilation(copyMinimalSources, outputKlib, fullStdlibJarProvider)
    
    args("-nowarn") 
}

val compileMinimalStdlib by tasks.registering {
    dependsOn(compileStdlib)
}

val distJKlib by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
val distMinimalJKlib by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    extendsFrom(distJKlib)
}

artifacts {
    add(distJKlib.name, outputKlib) {
        builtBy(compileStdlib)
    }
    add(distJKlib.name, fullStdlibJarProvider)
}
