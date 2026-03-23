#!/bin/bash

# test_jklib_stdlib.sh
# Creates and runs the JKlib minimal stdlib using the K2JKlibCompiler.

set -e # Exit on error

# Find workspace root
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "Working directory: $ROOT_DIR"

# Constants
STDLIB_SRC_DIR="$ROOT_DIR/prepare/jklib-compiler/stdlib-src"
OUTPUT_KLIB="$ROOT_DIR/prepare/jklib-compiler/kotlin-stdlib.klib"
JKLIB_COMPILER_JAR="$ROOT_DIR/dist/jklib/lib/kotlin-jklib-compiler.jar"

# dependencies constants
STDLIB_JAR="$ROOT_DIR/dist/kotlinc/lib/kotlin-stdlib.jar"
REFLECT_JAR="$ROOT_DIR/dist/kotlinc/lib/kotlin-reflect.jar"
RUNTIME_SCRIPT_JAR="$ROOT_DIR/dist/kotlinc/lib/kotlin-script-runtime.jar"
COROUTINES_JAR="$ROOT_DIR/dist/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
ANNOTATIONS_JAR="$ROOT_DIR/dist/kotlinc/lib/annotations-13.0.jar"
COMPILER_CLASSPATH="$JKLIB_COMPILER_JAR:$STDLIB_JAR:$REFLECT_JAR:$RUNTIME_SCRIPT_JAR:$COROUTINES_JAR:$ANNOTATIONS_JAR"

# 1. Do - Build Compiler with Proguard and verify
echo "Building :kotlin-jklib-compiler:dist with Proguard..."
if ! "$ROOT_DIR/gradlew" -q :kotlin-jklib-compiler:dist -Pkotlin.build.proguard=true > gradlew.log 2>&1; then
    cat gradlew.log
    echo "Error: Failed to build :kotlin-jklib-compiler:dist"
    exit 1
fi
[ -s "$JKLIB_COMPILER_JAR" ] || (echo "Error: Compiler JAR missing at $JKLIB_COMPILER_JAR"; exit 1)

# 1. Verify - the JAR contains the K2JKlibCompiler class
if ! jar -tf "$JKLIB_COMPILER_JAR" | grep -q "org/jetbrains/kotlin/cli/jklib/K2JKlibCompiler.class" 
then
    echo "Error: K2JKlibCompiler.class not found in the output JAR!"
    exit 1
fi

# 2. build dependencies (populates dist/kotlinc/lib)
echo "Building :kotlin-compiler:distKotlinc..."
if ! "$ROOT_DIR/gradlew" -q :kotlin-compiler:distKotlinc > gradlew.log 2>&1; then
    cat gradlew.log
    echo "Error: Failed to build dependencies via :kotlin-compiler:distKotlinc"
    exit 1
fi

## 2. verify - the resulting jars are present
[ -s "$STDLIB_JAR" ] || (echo "Error: Stdlib JAR missing at $STDLIB_JAR"; exit 1)
[ -s "$REFLECT_JAR" ] || (echo "Error: Reflect JAR missing at $REFLECT_JAR"; exit 1)


# 3. collect sources
echo "Collecting sources..."
mkdir -p "$STDLIB_SRC_DIR/common/kotlin"
mkdir -p "$STDLIB_SRC_DIR/jvm"

# Function to copy and verify
cp_v() {
    local src="$ROOT_DIR/$1"
    local dest="$STDLIB_SRC_DIR/$2"
    local dir=$(dirname "$dest")
    mkdir -p "$dir"
    cp "$src" "$dest"
    [ -s "$dest" ] || (echo "Error: Failed to copy or verify $src -> $dest"; exit 1)
}

# 3.1 Common Sources
# 3.1.1 Stubs
for f in $(find "$ROOT_DIR/libraries/stdlib/jklib-for-test/src/stubs/kotlin" -name "*.kt"); do
    rel_path=${f#$ROOT_DIR/libraries/stdlib/jklib-for-test/src/stubs/}
    cp_v "libraries/stdlib/jklib-for-test/src/stubs/$rel_path" "common/$rel_path"
done

# 3.1.2 Core sources
CORE_COMMON=(
    "kotlin/Annotation.kt" "kotlin/Annotations.kt" "kotlin/Any.kt" "kotlin/Array.kt"
    "kotlin/ArrayIntrinsics.kt" "kotlin/Arrays.kt" "kotlin/Boolean.kt" "kotlin/CharSequence.kt"
    "kotlin/Comparable.kt" "kotlin/Enum.kt" "kotlin/Function.kt" "kotlin/Iterator.kt"
    "kotlin/Library.kt" "kotlin/Nothing.kt" "kotlin/Number.kt" "kotlin/String.kt"
    "kotlin/Throwable.kt" "kotlin/Primitives.kt" "kotlin/Unit.kt" "kotlin/util/Lateinit.kt"
    "kotlin/annotation/Annotations.kt" "kotlin/annotations/Multiplatform.kt" "kotlin/annotations/WasExperimental.kt"
    "kotlin/annotations/ReturnValue.kt" "kotlin/internal/Annotations.kt" "kotlin/internal/AnnotationsBuiltin.kt"
    "kotlin/concurrent/atomics/AtomicArrays.common.kt" "kotlin/concurrent/atomics/Atomics.common.kt"
    "kotlin/contextParameters/Context.kt" "kotlin/contextParameters/ContextOf.kt"
    "kotlin/contracts/ContractBuilder.kt" "kotlin/contracts/Effect.kt"
)
for f in "${CORE_COMMON[@]}"; do
    cp_v "libraries/stdlib/src/$f" "common/$f"
done
# 3.1.3 ExceptionsH
cp_v "libraries/stdlib/common/src/kotlin/ExceptionsH.kt" "common/kotlin/ExceptionsH.kt"

# 3.2 JVM Sources
# Stubs JVM
for f in $(find "$ROOT_DIR/libraries/stdlib/jklib-for-test/src/stubs/jvm/builtins" -name "*.kt"); do
    rel_path=${f#$ROOT_DIR/libraries/stdlib/jklib-for-test/src/stubs/jvm/}
    cp_v "libraries/stdlib/jklib-for-test/src/stubs/jvm/$rel_path" "jvm/$rel_path"
done

# JVM core sources
CORE_JVM=(
    "runtime/kotlin/NoWhenBranchMatchedException.kt" "runtime/kotlin/UninitializedPropertyAccessException.kt"
    "runtime/kotlin/TypeAliases.kt" "runtime/kotlin/text/TypeAliases.kt"
    "src/kotlin/ArrayIntrinsics.kt" "src/kotlin/Unit.kt"
    "src/kotlin/collections/TypeAliases.kt" "src/kotlin/enums/EnumEntriesJVM.kt"
    "src/kotlin/io/Serializable.kt"
)
for f in "${CORE_JVM[@]}"; do
    cp_v "libraries/stdlib/jvm/$f" "jvm/$f"
done

# JVM builtins (excluding Char and Collections)
for f in $(ls "$ROOT_DIR/libraries/stdlib/jvm/builtins/"*.kt); do
    base=$(basename "$f")
    if [[ "$base" != "Char.kt" && "$base" != "Collections.kt" ]]; then
        cp_v "libraries/stdlib/jvm/builtins/$base" "jvm/builtins/$base"
    fi
done

# Minimal atomics and throwables
cp_v "libraries/stdlib/jvm-minimal-for-test/jvm-src/minimalAtomics.kt" "jvm/minimalAtomics.kt"
cp_v "libraries/stdlib/jvm-minimal-for-test/jvm-src/minimalThrowables.kt" "jvm/minimalThrowables.kt"

# 4. compile minimal stdlib
echo "Compiling minimal stdlib..."
# Prepare lists
COMMON_SOURCES_ARGS=$(find "$STDLIB_SRC_DIR/common" -name "*.kt" | tr '\n' ',')
COMMON_SOURCES_ARGS=${COMMON_SOURCES_ARGS%,} # remove trailing comma
COMMON_SOURCES_LIST=$(find "$STDLIB_SRC_DIR/common" -name "*.kt")
JVM_SOURCES_LIST=$(find "$STDLIB_SRC_DIR/jvm" -name "*.kt")

COMPILER_CP="$JKLIB_COMPILER_JAR:$STDLIB_JAR:$REFLECT_JAR:$ROOT_DIR/dist/kotlinc/lib/kotlin-script-runtime.jar:$ROOT_DIR/dist/kotlinc/lib/kotlinx-coroutines-core-jvm.jar:$ROOT_DIR/dist/kotlinc/lib/annotations-13.0.jar"

java -cp "$COMPILER_CP" \
    org.jetbrains.kotlin.cli.jklib.K2JKlibCompiler \
    -no-stdlib \
    -Xallow-kotlin-package \
    -Xexpect-actual-classes \
    -module-name kotlin-stdlib \
    -Xstdlib-compilation \
    -d "$OUTPUT_KLIB" \
    -Xmulti-platform \
    -opt-in=kotlin.contracts.ExperimentalContracts \
    -opt-in=kotlin.ExperimentalMultiplatform \
    -opt-in=kotlin.contracts.ExperimentalExtendedContracts \
    -Xcompile-builtins-as-part-of-stdlib \
    -Xreturn-value-checker=full \
    -Xcommon-sources="$COMMON_SOURCES_ARGS" \
    -classpath "$COMPILER_CP" \
    -nowarn \
    $JVM_SOURCES_LIST $COMMON_SOURCES_LIST 

[ -s "$OUTPUT_KLIB" ] || (echo "Error: KLIB generation failed"; exit 1)

# 5. Compile example with minimal stdlib
echo "Verifying with String length example..."
TEST_HELLO_KT="$ROOT_DIR/prepare/jklib-compiler/test_hello.kt"
TEST_HELLO_KLIB="$ROOT_DIR/prepare/jklib-compiler/test_hello.klib"

echo 'fun main() { 
    val s = "JKlib Success"
    if (s.length != 13) throw Exception("Length mismatch")
}' > "$TEST_HELLO_KT"
java -cp "$COMPILER_CP" \
    org.jetbrains.kotlin.cli.jklib.K2JKlibCompiler \
    -no-stdlib \
    -Xklib="$OUTPUT_KLIB" \
    "$TEST_HELLO_KT" \
    -d "$TEST_HELLO_KLIB"

[ -s "$TEST_HELLO_KLIB" ] || (echo "Error: Test verification failed"; exit 1)

# Step 6: Cleanup (Success Only)
echo "Cleaning up..."
rm -rf "$STDLIB_SRC_DIR"
rm "$OUTPUT_KLIB"
rm "$TEST_HELLO_KT"
rm "$TEST_HELLO_KLIB"

echo "========================================"
echo "All steps completed successfully!"
echo "========================================"