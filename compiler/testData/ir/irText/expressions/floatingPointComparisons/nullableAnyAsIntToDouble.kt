// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB
fun test(x: Any?, y: Double) =
    x is Int && x < y
