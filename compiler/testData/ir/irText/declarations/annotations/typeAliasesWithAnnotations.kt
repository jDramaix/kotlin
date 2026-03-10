// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB
@Target(AnnotationTarget.TYPEALIAS)
annotation class TestAnn(val x: String)

@TestAnn("TestTypeAlias")
typealias TestTypeAlias = String
