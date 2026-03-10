// FIR_IDENTICAL
// IGNORE_BACKEND: JKLIB

val test1 = arrayOf<String>()
val test2 = arrayOf("1", "2", "3")
val test3 = arrayOf("0", *test2, *test1, "4")
