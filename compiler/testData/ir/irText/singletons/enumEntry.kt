// KT-75481
// IGNORE_BACKEND: JKLIB
// SKIP_NEW_KOTLIN_REFLECT_COMPATIBILITY_CHECK
enum class Z {
    ENTRY {
        fun test() {}

        inner class A {
            fun test2() {
                test()
            }
        }
    }
}
