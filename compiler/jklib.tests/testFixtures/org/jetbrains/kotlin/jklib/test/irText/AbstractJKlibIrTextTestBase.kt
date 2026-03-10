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

        useMetaTestConfigurators(::FirSpecificParserSuppressor, ::WithStdlibSkipper, ::WithReflectSkipper, ::MuteListSkipper)

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

class MuteListSkipper(testServices: org.jetbrains.kotlin.test.services.TestServices) :
    org.jetbrains.kotlin.test.services.MetaTestConfigurator(testServices) {
    // TODO: joseefort - fix minimal stdlib to get all tests to match
    companion object {
        private val mutedTests = setOf(

            // Basic Reflect Classes not included in minimal stdlib failures
            "testBoundCallableReferences", // Missing class/es: kotlin.reflect.KProperty0
            "testClassLiteralInAnnotation", // Missing class/es: kotlin.reflect.KClass
            "testConstExpressionsInAnnotationArguments", // Missing class/es: kotlin.reflect.KType
            "testConstFromBuiltins", // Missing class/es: kotlin.reflect.KType
            "testConstValInitializers", // Missing class/es: kotlin.reflect.KType
            "testDelegateForExtPropertyInClass", // Missing class/es: kotlin.reflect.KProperty, kotlin.reflect.KProperty2
            "testDelegatedPropertyAccessorsWithAnnotations", // Missing class/es: kotlin.reflect.KProperty0, kotlin.reflect.KMutableProperty0
            "testGenericDelegatedProperty", // Missing class/es: kotlin.reflect.KMutableProperty1
            "testGenericMember", // Missing class/es: kotlin.reflect.KProperty1
            "testGenericPropertyRef", // Missing class/es: kotlin.reflect.KProperty1, kotlin.reflect.KMutableProperty1
            "testGenericPropertyReferenceType", // Missing class/es: kotlin.reflect.KMutableProperty, kotlin.reflect.KMutableProperty0
            "testImportedFromObject", // Missing class/es: kotlin.reflect.KProperty0
            "testKt28006", // Missing class/es: kotlin.reflect.KType
            "testKt52677", // Missing class/es: kotlin.annotation.Target, kotlin.reflect.KClass, kotlin.annotation.AnnotationTarget
            "testLocal", // Missing class/es: kotlin.reflect.KProperty0
            "testMember", // Missing class/es: kotlin.reflect.KProperty1
            "testMemberExtension", // Missing class/es: kotlin.reflect.KProperty2
            "testTopLevel", // Missing class/es: kotlin.reflect.KProperty0
            // Basic Reflect Classes not included in minimal stdlib failures

            // Symbol resolution compilation failures due to stdlib mismatches
            "testAnnotationRetentions", // Missing standard library annotations (@Retention and its values SOURCE, BINARY, RUNTIME) on the classpath.
            "testAnnotationRetentionsMultiModule", // Missing standard library annotations (@Retention, @Target, and their values) in a multi-module context.
            "testArgumentMappedWithError", // Unresolved TODO() call, likely due to a missing or inaccessible kotlin.util package.
            "testArrayAssignment", // Resolution ambiguity for intArrayOf and missing set operator.
            "testArrayAugmentedAssignment1", // Overload resolution ambiguity for intArrayOf.
            "testArrayInAnnotationArguments", // Ambiguous resolution of intArrayOf and arrayOf within annotation arguments.
            "testArraysFromBuiltins", // Failure to access IntIterator, causing the IntArray.iterator() call to fail.
            "testAssignmentOperator", // Resolution ambiguity for arrayOf when initializing an array property.
            "testBadBreakContinue", // Invalid break or continue statements that cross function/class boundaries or are placed outside of loops.
            "testBreakContinueInWhen", // Missing or inaccessible IntIterator during a for-loop iteration over a range.
            "testCapturedTypeInFakeOverride", // Unresolved reference to TODO() in a method that overrides a generic class member with a captured type.
            "testCoercionInLoop", // Missing DoubleIterator for iterating over a DoubleArray.
            "testContextWithAnnotation", // Unresolved annotation targets (TYPE, VALUE_PARAMETER, FUNCTION, PROPERTY) and AnnotationTarget.
            "testDefinitelyNotNullWithIntersection1", // Overload resolution ambiguity for arrayOf and type inference failure for foo.
            "testExtensionLambda", // Unresolved reference to TODO() within an extension lambda.
            "testFileAnnotations", // Unresolved reference to Target and AnnotationTarget in file-level annotations.
            "testFloatingPointCompareTo", // Unresolved reference to TODO() in floating-point comparison logic.
            "testFloatingPointLess", // Unresolved reference to TODO() in floating-point inequality checks.
            "testGenericAnnotationClasses", // Unresolved reference to Retention, Target, and associated standard library constants in generic annotations.
            "testIfWithArrayOperation", // Overload resolution ambiguity for intArrayOf within an if expression.
            "testIncrementDecrement", // Missing IntIterator and loop methods (next, hasNext) during increment/decrement operations in a loop.
            "testIndependentBackingFieldType", // Unresolved reference to TODO() in a property with an independent backing field type.
            "testInitValInLambda", // Unresolved reference to TODO() when initializing a property within a lambda.
            "testIntegerCoercionToT", // Unresolved reference to TODO() during integer-to-generic-type coercion.
            "testIntersectionType1", // Overload resolution ambiguity for arrayOf when working with intersection types.
            "testKt27005", // Unresolved Target and AnnotationTarget when using @Target on an annotation class.
            "testKt37570", // Unresolved apply, type mismatch, and captured variable initialization constraints.
            "testKt37779", // Overload resolution ambiguity for arrayOf.
            "testKt46069", // Unresolved reference for standard library function let.
            "testKt50028", // Unresolved reference for NotImplementedError, indicating a missing standard exception class.
            "testLambdaWithParameterName", // Unresolved TODO() call within a higher-order function.
            "testMultipleImplicitReceivers", // Unresolved references for with and member functions when using multiple implicit receivers.
            "testNewInferenceFixationOrder1", // Unresolved TODO() during complex type inference fixation.
            "testNoErrorTypeAfterCaptureApproximation", // Unresolved TODO() following generic type capture approximation.
            "testNullableAnyAsIntToDouble", // Argument type mismatch in floating-point comparison: expected Int, got Double.
            "testSpecialAnnotationsMetadata", // Unresolved internal compiler annotations (Exact, NoInfer) from kotlin.internal.
            "testSpreadOperatorInAnnotationArguments", // Overload resolution ambiguity for arrayOf when used with the spread operator in annotations.
            "testTargetOnPrimaryCtorParameter", // Unresolved Target, AnnotationTarget, and VALUE_PARAMETER, typical of missing annotation infrastructure.
            "testTypeAliasesWithAnnotations", // Unresolved annotation targets and applicability errors specific to typealias declarations.
            "testTypeParameterBounds", // Missing standard annotation classes and applicability issues when annotating type parameters and their bounds.
            "testTypeParametersWithAnnotations", // Unresolved annotation references and applicability failures for type parameter annotations.
            "testVararg", // Overload resolution ambiguity for arrayOf and invalid spread operator usage on nullable types.
            "testVarargWithImplicitCast", // Overload resolution ambiguity for intArrayOf in the presence of implicit type casts.
            "testWhenReturnUnit", // Unresolved TODO() call within a when expression branch.
            "testWhenWithSubjectVariable", // Unresolved reference contains, preventing the use of in checks within when expressions.
            "testWithVarargViewedAsArray", // Missing IntIterator and associated loop methods (next, hasNext), preventing iteration over primitive arrays.
            // End of Symbol resolution compilation failures due to stdlib mismatches

            "testJavaRecordComponentAccess", // [Other Error] at org.jetbrains.kotlin.test.util.KtTestUtil.getJdkHome(KtTestUtil.java:143)

            //IR mismatch failures
            "testClasses", // EnumEntries<T> simplified to raw EnumEntries (removal of type argument).
            "testClassesWithAnnotations", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumClassModality", // EnumEntries<T> simplified to raw EnumEntries across multiple enum definitions.
            "testEnumEntriesWithAnnotations", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumEntry", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumEntryAsReceiver", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumEntryReferenceFromEnumEntryClass", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumWithMultipleCtors", // EnumEntries<T> simplified to raw EnumEntries.
            "testEnumsInAnnotationArguments", // EnumEntries<T> simplified to raw EnumEntries.
            "testExhaustiveWhenElseBranch", // EnumEntries<T> simplified to raw EnumEntries.
            "testGenericConstructorCallWithTypeArguments", // Updated times call argument from Int to Long (3L).
            "testIfWithLoop", // IntIterator generalized to Iterator<Int> in FOR_LOOP ranges.
            "testKotlinInnerClass", // Added [expect] tags to Any methods and constructors.
            "testKt47245", // Generalized IntIterator to Iterator<Int> in FOR_LOOP.
            "testLateinitPropertiesSeparateModule", // Removed module markers from the diff output.
            "testMultiList", // Collection interface methods use Collection as receiver type instead of List.
            "testObjectAsCallable", // EnumEntries<T> simplified to raw EnumEntries.
            "testReflectionLiterals", // KClass<T> references reported as IrErrorType due to inconsistent type arguments.
            "testSamConversionClassInProjection", // Added IMPLICIT_CAST for Function2 and updated SAM_CONVERSION types.
            "testSimpleOperators", // kotlin.ranges.IntRange simplified to kotlin.IntRange.
            "testStaticOverrideOnKJJ", // Removed ACCIDENTAL_OVERRIDE diagnostics from the test data.
            "testSubstitutionFakeOverrides2", // Removed a redundant FAKE_OVERRIDE entry for foo.
            "testTemporaryInEnumEntryInitializer", // EnumEntries<T> simplified to raw EnumEntries.
            "testTypeParameterClassLiteral", // KClass<T> references for reified type parameters reported as IrErrorType.
            "testValues", // EnumEntries<T> simplified to raw EnumEntries.
            "testWhenSmartCastToEnum", // EnumEntries<T> simplified to raw EnumEntries.
            // End of IR mismatches

            // IR mismatch pending multi module setup
            "testActualizeInterfaceAsAny",
            "testExpectClassInherited",
            "testExpectIntersectionOverride",
            "testExpectMemberInNotExpectClass",
            "testExpectedEnumClass",
            "testExpectedFun",
            "testExpectedSealedClass",
            "testFirBuilder",
            "testGenericClassInDifferentModule",
            "testInternalOverrideCrossModule",
            "testInternalOverrideWithFriendModule",
            "testInternalPotentialFakeOverride",
            "testInternalPotentialOverride",
            "testInternalWithPublishedApiOverride",
            "testIntersectionWithPublishedApiOverride",
            // end of pending multi module setup
        )
    }

    override val directiveContainers: List<org.jetbrains.kotlin.test.directives.model.DirectivesContainer>
        get() = emptyList()

    override fun shouldSkipTest(): Boolean {
        val originalFile = testServices.moduleStructure.originalTestDataFiles.first()
        val name = originalFile.nameWithoutExtension
        val testName =
            "test" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        return testName in mutedTests
    }
}
