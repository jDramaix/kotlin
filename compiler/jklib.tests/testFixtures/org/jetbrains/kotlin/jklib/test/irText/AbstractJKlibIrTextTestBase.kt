package org.jetbrains.kotlin.jklib.test.irText

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.*
import org.jetbrains.kotlin.test.configuration.commonHandlersForCodegenTest
import org.jetbrains.kotlin.test.configuration.setupDefaultDirectivesForIrTextTest
import org.jetbrains.kotlin.test.configuration.setupIrTextDumpHandlers
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.configuration.commonServicesConfigurationForCodegenAndDebugTest
import org.jetbrains.kotlin.test.services.PhasedPipelineChecker
import org.jetbrains.kotlin.test.services.TestPhase
import org.jetbrains.kotlin.test.backend.handlers.NoFirCompilationErrorsHandler
import org.jetbrains.kotlin.test.builders.firHandlersStep
import org.jetbrains.kotlin.test.builders.irHandlersStep
import org.jetbrains.kotlin.test.builders.klibArtifactsHandlersStep
import org.jetbrains.kotlin.utils.bind

import org.jetbrains.kotlin.test.directives.CodegenTestDirectives

import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

import org.jetbrains.kotlin.test.services.configuration.CommonEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.JvmEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.JvmForeignAnnotationsConfigurator
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.sourceProviders.AdditionalDiagnosticsSourceFilesProvider
import org.jetbrains.kotlin.test.services.sourceProviders.CoroutineHelpersSourceFilesProvider
import org.jetbrains.kotlin.test.services.fir.FirSpecificParserSuppressor


abstract class AbstractJKlibIrTextTestBase<FrontendOutput : ResultingArtifact.FrontendOutput<FrontendOutput>>(
    val targetFrontend: FrontendKind<FrontendOutput>
) : AbstractKotlinCompilerWithTargetBackendTest(TargetBackend.JVM_IR) {
    abstract val frontendFacade: Constructor<FrontendFacade<FrontendOutput>>
    abstract val frontendToBackendConverter: Constructor<Frontend2BackendConverter<FrontendOutput, IrBackendInput>>
    abstract val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.KLib>>

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        // Custom configuration for JKlib
        // Replicated from commonServicesConfigurationForCodegenAndDebugTest but without ScriptingEnvironmentConfigurator
        globalDefaults {
            frontend = targetFrontend
            targetBackend = TargetBackend.JVM_IR
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

        useMetaTestConfigurators(::FirSpecificParserSuppressor, ::WithStdlibSkipper /*, ::MuteListSkipper */)

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

class WithStdlibSkipper(testServices: org.jetbrains.kotlin.test.services.TestServices) : org.jetbrains.kotlin.test.services.MetaTestConfigurator(testServices) {
    override val directiveContainers: List<org.jetbrains.kotlin.test.directives.model.DirectivesContainer>
        get() = listOf(org.jetbrains.kotlin.test.directives.ConfigurationDirectives)

    override fun shouldSkipTest(): Boolean {
        return testServices.moduleStructure.allDirectives.contains(org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB)
    }
}

class MuteListSkipper(testServices: org.jetbrains.kotlin.test.services.TestServices) : org.jetbrains.kotlin.test.services.MetaTestConfigurator(testServices) {
    // TODO: joseefort - fix minimal stdlib to get all tests to match
    companion object {
        private val mutedTests = setOf(
            "testActualizeInterfaceAsAny", // [IR mismatch] Actual data differs from file content
            "testAnnotationRetentions", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testAnnotationRetentionsMultiModule", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testArgumentMappedWithError", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testArrayAssignment", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testArrayAugmentedAssignment1", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testArrayInAnnotationArguments", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testArraysFromBuiltins", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testAssignmentOperator", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testBadBreakContinue", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testBoundCallableReferences", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testBreakContinueInWhen", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testCapturedTypeInFakeOverride", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testClassLiteralInAnnotation", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testClasses", // [IR mismatch] Actual data differs from file content
            "testClassesWithAnnotations", // [IR mismatch] Actual data differs from file content
            "testCoercionInLoop", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testConstExpressionsInAnnotationArguments", // [Backend Crash/IllegalStateException] CALL 'public final fun <get-ONE> (): kotlin.Int declared in <root>' type=kotlin....
            "testConstFromBuiltins", // [Backend Crash/IllegalStateException] CALL 'public final fun <get-MIN_VALUE> (): kotlin.Int declared in kotlin.Int.Com...
            "testConstValInitializers", // [Backend Crash/IllegalStateException] CALL 'public final fun plus (other: kotlin.Int): kotlin.Int [expect,operator] de...
            "testContextWithAnnotation", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testDefinitelyNotNullWithIntersection1", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testDelegateForExtPropertyInClass", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testDelegatedPropertyAccessorsWithAnnotations", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testEnumClassModality", // [IR mismatch] Actual data differs from file content
            "testEnumEntriesWithAnnotations", // [IR mismatch] Actual data differs from file content
            "testEnumEntry", // [IR mismatch] Actual data differs from file content
            "testEnumEntryAsReceiver", // [IR mismatch] Actual data differs from file content
            "testEnumEntryReferenceFromEnumEntryClass", // [IR mismatch] Actual data differs from file content
            "testEnumWithMultipleCtors", // [IR mismatch] Actual data differs from file content
            "testEnumsInAnnotationArguments", // [IR mismatch] Actual data differs from file content
            "testExhaustiveWhenElseBranch", // [IR mismatch] Actual data differs from file content
            "testExpectClassInherited", // [IR mismatch] Actual data differs from file content
            "testExpectIntersectionOverride", // [IR mismatch] Actual data differs from file content
            "testExpectMemberInNotExpectClass", // [IR mismatch] Actual data differs from file content
            "testExpectedEnumClass", // [IR mismatch] Actual data differs from file content
            "testExpectedFun", // [IR mismatch] Actual data differs from file content
            "testExpectedSealedClass", // [IR mismatch] Actual data differs from file content
            "testExtensionLambda", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testFileAnnotations", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testFirBuilder", // [IR mismatch] Actual data differs from file content
            "testFloatingPointCompareTo", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testFloatingPointLess", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testGenericAnnotationClasses", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testGenericClassInDifferentModule", // [IR mismatch] Actual data differs from file content
            "testGenericConstructorCallWithTypeArguments", // [IR mismatch] Actual data differs from file content
            "testGenericDelegatedProperty", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testGenericMember", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testGenericPropertyRef", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testGenericPropertyReferenceType", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIfWithArrayOperation", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIfWithLoop", // [IR mismatch] Actual data differs from file content
            "testImportedFromObject", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIncrementDecrement", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIndependentBackingFieldType", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testInitValInLambda", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIntegerCoercionToT", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testInternalOverrideCrossModule", // [IR mismatch] Actual data differs from file content
            "testInternalOverrideWithFriendModule", // [IR mismatch] Actual data differs from file content
            "testInternalPotentialFakeOverride", // [IR mismatch] Actual data differs from file content
            "testInternalPotentialOverride", // [IR mismatch] Actual data differs from file content
            "testInternalWithPublishedApiOverride", // [IR mismatch] Actual data differs from file content
            "testIntersectionType1", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testIntersectionWithPublishedApiOverride", // [IR mismatch] Actual data differs from file content
            "testJavaRecordComponentAccess", // [Other Error] at org.jetbrains.kotlin.test.util.KtTestUtil.getJdkHome(KtTestUtil.java:143)
            "testKotlinInnerClass", // [IR mismatch] Actual data differs from file content
            "testKt27005", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testKt28006", // [Backend Crash/IllegalStateException] STRING_CONCATENATION type=kotlin.String
            "testKt37570", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testKt37779", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testKt46069", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testKt47245", // [IR mismatch] Actual data differs from file content
            "testKt50028", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testKt52677", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testLambdaWithParameterName", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testLateinitPropertiesSeparateModule", // [IR mismatch] Actual data differs from file content
            "testLocal", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testMember", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testMemberExtension", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testMultiList", // [IR mismatch] Actual data differs from file content
            "testMultipleImplicitReceivers", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testNewInferenceFixationOrder1", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testNoErrorTypeAfterCaptureApproximation", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testNullableAnyAsIntToDouble", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testObjectAsCallable", // [IR mismatch] Actual data differs from file content
            "testReflectionLiterals", // [IR mismatch] Actual data differs from file content
            "testSamConversionClassInProjection", // [IR mismatch] Actual data differs from file content
            "testSimpleOperators", // [IR mismatch] Actual data differs from file content
            "testSpecialAnnotationsMetadata", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testSpreadOperatorInAnnotationArguments", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testStaticOverrideOnKJJ", // [IR mismatch] Actual data differs from file content
            "testSubstitutionFakeOverrides2", // [IR mismatch] Actual data differs from file content
            "testTargetOnPrimaryCtorParameter", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testTemporaryInEnumEntryInitializer", // [IR mismatch] Actual data differs from file content
            "testTopLevel", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testTypeAliasesWithAnnotations", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testTypeParameterBounds", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testTypeParameterClassLiteral", // [IR mismatch] Actual data differs from file content
            "testTypeParametersWithAnnotations", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testValues", // [IR mismatch] Actual data differs from file content
            "testVararg", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testVarargWithImplicitCast", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testWhenReturnUnit", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testWhenSmartCastToEnum", // [IR mismatch] Actual data differs from file content
            "testWhenWithSubjectVariable", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
            "testWithVarargViewedAsArray", // [Backend Crash/IllegalStateException] java.lang.IllegalStateException: CLI phase failed with errors:
        )
    }

    override val directiveContainers: List<org.jetbrains.kotlin.test.directives.model.DirectivesContainer>
        get() = emptyList()

    override fun shouldSkipTest(): Boolean {
        val originalFile = testServices.moduleStructure.originalTestDataFiles.first()
        val name = originalFile.nameWithoutExtension
        val testName = "test" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        return testName in mutedTests
    }
}
