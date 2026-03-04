package org.jetbrains.kotlin.generators.tests

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.jklib.test.irText.AbstractFirJKlibIrTextTest
import org.jetbrains.kotlin.jklib.test.irText.AbstractFirLightTreeJKlibIrTextTest

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val testsRoot = args.getOrNull(0) ?: "compiler/jklib.tests/build/tests-gen" // Optional fallback if args not used by default
    val mainClassName = "org.jetbrains.kotlin.generators.tests.GenerateJklibTestsKt"

    generateTestGroupSuiteWithJUnit5(args, mainClassName) {
        testGroup(testsRoot, "compiler/testData") {
            testClass<AbstractFirLightTreeJKlibIrTextTest> {
                model("ir/irText", excludeDirs = listOf("declarations/multiplatform/k1"))
            }
            testClass<AbstractFirJKlibIrTextTest> {
                model("ir/irText", excludeDirs = listOf("declarations/multiplatform/k1"))
            }
        }
    }
}
