// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB
@file:A("File annotation")
package test

@Target(AnnotationTarget.FILE)
annotation class A(val x: String)
