// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB

@Target(AnnotationTarget.TYPE_PARAMETER)
annotation class Anno

fun <@Anno T> foo() {}
