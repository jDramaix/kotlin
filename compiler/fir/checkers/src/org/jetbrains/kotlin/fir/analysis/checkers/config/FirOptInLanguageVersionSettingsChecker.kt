/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.config

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext

object FirOptInLanguageVersionSettingsChecker : FirLanguageVersionSettingsChecker() {
    override fun check(context: CheckerContext, rawReport: (Boolean, String) -> Unit) {
    }
}