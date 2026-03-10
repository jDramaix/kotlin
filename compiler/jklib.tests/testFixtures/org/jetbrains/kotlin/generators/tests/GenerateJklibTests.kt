/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.jklib.test.irText.AbstractFirJKlibIrTextTest

fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val testsRoot = args.getOrNull(0) ?: "compiler/jklib.tests/build/tests-gen" // Optional fallback if args not used by default
    val mainClassName = "org.jetbrains.kotlin.generators.tests.GenerateJklibTestsKt"

    generateTestGroupSuiteWithJUnit5(args, mainClassName) {
        testGroup(testsRoot, "compiler/testData") {
            testClass<AbstractFirJKlibIrTextTest> {
                model("ir/irText", excludeDirs = listOf("declarations/multiplatform/k1"))
            }
        }
    }
}
