// Stubbed to provide a minimal dependency-free implementation of Arrays.asList for the compiler backend 
// without bringing in the full standard library interoperability layer. 
// jvm-minimal-for-test includes the real source for this.
package kotlin.collections

import java.util.Arrays

internal object ArraysUtilJVM {
    @JvmStatic
    fun <T> asList(array: Array<T>): MutableList<T> {
        return Arrays.asList(*array)
    }
}
