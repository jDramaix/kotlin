// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB
enum class En { A, B, C, D }

annotation class TestAnn(val x: En)

@TestAnn(En.A)
fun test1() {}
