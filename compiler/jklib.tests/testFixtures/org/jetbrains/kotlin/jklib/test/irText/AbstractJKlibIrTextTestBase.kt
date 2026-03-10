/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jklib.test.irText

import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.handlers.NoFirCompilationErrorsHandler
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.*
import org.jetbrains.kotlin.test.configuration.setupDefaultDirectivesForIrTextTest
import org.jetbrains.kotlin.test.configuration.setupIrTextDumpHandlers
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.services.configuration.CommonEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.fir.FirSpecificParserSuppressor
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.sourceProviders.AdditionalDiagnosticsSourceFilesProvider
import org.jetbrains.kotlin.test.services.sourceProviders.CoroutineHelpersSourceFilesProvider
import org.jetbrains.kotlin.utils.bind


abstract class AbstractJKlibIrTextTestBase<FrontendOutput : ResultingArtifact.FrontendOutput<FrontendOutput>>(
    val targetFrontend: FrontendKind<FrontendOutput>,
) : AbstractKotlinCompilerWithTargetBackendTest(TargetBackend.JKLIB) {
    abstract val frontendFacade: Constructor<FrontendFacade<FrontendOutput>>
    abstract val frontendToBackendConverter: Constructor<Frontend2BackendConverter<FrontendOutput, IrBackendInput>>
    abstract val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.KLib>>

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        // Custom configuration for JKlib
        // Replicated from commonServicesConfigurationForCodegenAndDebugTest but without ScriptingEnvironmentConfigurator
        globalDefaults {
            frontend = targetFrontend
            targetBackend = TargetBackend.JKLIB
            targetPlatform = JvmPlatforms.defaultJvmPlatform
            artifactKind = ArtifactKinds.KLib
            dependencyKind = DependencyKind.Binary
        }

        useConfigurators(
            ::CommonEnvironmentConfigurator,
            ::JKlibSourceRootConfigurator,
            ::JKlibJavaSourceConfigurator,
        )

        useAdditionalSourceProviders(
            ::AdditionalDiagnosticsSourceFilesProvider,
            ::CoroutineHelpersSourceFilesProvider,
        )

        useMetaTestConfigurators(::FirSpecificParserSuppressor, ::WithStdlibSkipper, ::WithReflectSkipper)

        facadeStep(frontendFacade)
        firHandlersStep {
            useHandlers(::NoFirCompilationErrorsHandler)
        }

        facadeStep(frontendToBackendConverter)
        irHandlersStep()

        facadeStep(backendFacade)
        klibArtifactsHandlersStep()

        setupDefaultDirectivesForIrTextTest()
        defaultDirectives {
            +CodegenTestDirectives.IGNORE_IR_EXPECT_FLAG
        }
        configureIrHandlersStep {
            setupIrTextDumpHandlers()
        }

        useAfterAnalysisCheckers(
            ::BlackBoxCodegenSuppressor,
            ::PhasedPipelineChecker.bind(TestPhase.BACKEND)
        )
        enableMetaInfoHandler()
    }
}

class WithStdlibSkipper(testServices: org.jetbrains.kotlin.test.services.TestServices) :
    org.jetbrains.kotlin.test.services.MetaTestConfigurator(testServices) {
    override val directiveContainers: List<org.jetbrains.kotlin.test.directives.model.DirectivesContainer>
        get() = listOf(org.jetbrains.kotlin.test.directives.ConfigurationDirectives)

    override fun shouldSkipTest(): Boolean {
        return testServices.moduleStructure.allDirectives.contains(org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB)
    }
}

class WithReflectSkipper(testServices: org.jetbrains.kotlin.test.services.TestServices) :
    org.jetbrains.kotlin.test.services.MetaTestConfigurator(testServices) {
    override val directiveContainers: List<org.jetbrains.kotlin.test.directives.model.DirectivesContainer>
        get() = listOf(JvmEnvironmentConfigurationDirectives)

    override fun shouldSkipTest(): Boolean {
        return testServices.moduleStructure.allDirectives.contains(JvmEnvironmentConfigurationDirectives.WITH_REFLECT)
    }
}
