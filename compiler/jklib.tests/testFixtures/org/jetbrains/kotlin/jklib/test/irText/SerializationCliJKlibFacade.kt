/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jklib.test.irText

import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.jklib.pipeline.JKlibFir2IrPipelineArtifact
import org.jetbrains.kotlin.cli.jklib.pipeline.JKlibKlibSerializationPhase
import org.jetbrains.kotlin.cli.jklib.pipeline.jklibOutputDestination
import org.jetbrains.kotlin.cli.pipeline.PipelineContext
import org.jetbrains.kotlin.config.phaser.PhaseConfig
import org.jetbrains.kotlin.config.phaser.invokeToplevel
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrCliBasedOutputArtifact
import org.jetbrains.kotlin.test.model.ArtifactKinds
import org.jetbrains.kotlin.test.model.BackendFacade
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.BinaryArtifacts
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.temporaryDirectoryManager
import org.jetbrains.kotlin.util.PerformanceManager
import org.jetbrains.kotlin.test.model.TestModule
import java.io.File

class SerializationCliJKlibFacade(testServices: TestServices) :
    BackendFacade<IrBackendInput, BinaryArtifacts.KLib>(testServices, BackendKinds.IrBackend, ArtifactKinds.KLib) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): BinaryArtifacts.KLib? {
        if (inputArtifact !is Fir2IrCliBasedOutputArtifact<*>) {
            error("input artifact is not a Fir2IrCliBasedOutputArtifact")
        }
        val cliArtifact = inputArtifact.cliArtifact
        require(cliArtifact is JKlibFir2IrPipelineArtifact)

        val tempDir = testServices.temporaryDirectoryManager.getOrCreateTempDirectory("jklib-${module.name}")
        val outputFile = File(tempDir, "result.klib")
        cliArtifact.configuration.jklibOutputDestination = outputFile.absolutePath

        val phaseConfig = PhaseConfig()
        val context = PipelineContext(
            object : PerformanceManager(JvmPlatforms.defaultJvmPlatform, "Test") {},
            kaptMode = false
        )

        JKlibKlibSerializationPhase.invokeToplevel(phaseConfig, context, cliArtifact)

        val diagnosticsReporter = cliArtifact.configuration.diagnosticsCollector

        return BinaryArtifacts.KLib(outputFile, diagnosticsReporter)
    }
}
