package app.morphe.patches.opencode.text

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.opencode.shared.Constants.COMPATIBILITY_OPENCODE_MOBILE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.util.TypeUtils

/**
 * How many lines a patched text may wrap to before the ellipsis kicks in.
 *
 * Five lines is enough for a shell command or a question option description without
 * letting a long string shove a conversation around.
 */
private const val MAX_LINES = 5

/**
 * Opcode used to write [MAX_LINES].
 *
 * `const/16` holds 0 to 32767. To never clip instead, set `MAX_LINES` to
 * `Int.MAX_VALUE` and change this to `Opcode.CONST`, which is the only opcode wide
 * enough — that is what Compose itself uses for "unlimited".
 */
private val MAX_LINES_OPCODE = Opcode.CONST_16

/**
 * Index of `maxLines` within [TextWrapperParameters].
 *
 * The register offset that follows from this is derived at runtime from the
 * fingerprinted wrapper's own parameter types rather than hardcoded, so the patch
 * survives an app release that inserts or drops a `Text` parameter.
 */
private const val MAX_LINES_PARAMETER_INDEX = 11

/** Index of the `$default` mask, the last argument of the wrapper. */
private const val DEFAULT_MASK_PARAMETER_INDEX = 17

/** `$default` mask bit that is set when the caller did not pass `maxLines`. */
private const val MAX_LINES_DEFAULTED_FLAG = 0x4000

private val CONST_OPCODES = setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST)

val maxLinesPatch = bytecodePatch(
    name = "Max lines",
    description = "Lets question option descriptions and tool call headers wrap onto more than one line.",
) {
    compatibleWith(COMPATIBILITY_OPENCODE_MOBILE)

    execute {
        // Resolve the shared Text wrapper. Call sites are matched against this
        // reference instead of a hardcoded class name, because R8 renames it on
        // every app release.
        val wrapper = TextWrapperFingerprint.match().methodReference

        raiseMaxLines(QuestionOptionDescriptionFingerprint, wrapper)
        raiseMaxLines(ToolDetailSheetHeaderFingerprint, wrapper)
    }
}

/**
 * Number of argument registers consumed by the first [parameterIndex] parameters of
 * [method]. Wide types (`J` and `D`) occupy two registers, which is why `maxLines`
 * sits at offset 15 for this wrapper rather than at 11.
 */
private fun registerOffsetOf(method: MethodReference, parameterIndex: Int): Int =
    method.parameterTypes
        .take(parameterIndex)
        .sumOf { if (TypeUtils.isWideType(it)) 2 else 1 }

/**
 * Raises `maxLines` at the text call that the fingerprint's last `methodCall` filter
 * matched, which for both fingerprints is the one currently clipped to one line.
 */
private fun raiseMaxLines(fingerprint: Fingerprint, wrapper: MethodReference) {
    val match = fingerprint.match()
    val method = match.method
    val instructions = method.implementation.instructions

    // The last declared filter is the `methodCall` for the text being changed,
    // so its instruction index is the call site itself.
    val callIndex = match.instructionMatches.last().index
    val call = instructions.elementAt(callIndex)

    check(call is RegisterRangeInstruction && call is ReferenceInstruction && call.reference == wrapper) {
        "${fingerprint.javaClass.simpleName} did not match the Text wrapper at instruction $callIndex"
    }
    call as RegisterRangeInstruction

    val firstRegister = call.startRegister
    val maxLinesRegister = firstRegister + registerOffsetOf(wrapper, MAX_LINES_PARAMETER_INDEX)
    val defaultMaskRegister = firstRegister + registerOffsetOf(wrapper, DEFAULT_MASK_PARAMETER_INDEX)

    if (isDefaulted(instructions, callIndex, defaultMaskRegister)) {
        // `maxLines` was never passed by this call site, so the wrapper is already
        // using Compose's unlimited default and there is nothing to change.
        return
    }

    // The new value is written immediately before the call rather than in place of the
    // existing constant: that argument register is only read by the call, so this
    // cannot disturb anything else, and it does not depend on which `const*` opcode
    // the compiler happened to pick for the original value.
    method.addInstruction(
        callIndex,
        "${MAX_LINES_OPCODE.name.lowercase().replace('_', '/')} v$maxLinesRegister, $MAX_LINES"
    )
}

/**
 * Whether the `$default` mask register holds a value with [MAX_LINES_DEFAULTED_FLAG]
 * set, meaning the caller never passed `maxLines`.
 */
private fun isDefaulted(
    instructions: MethodImplementation,
    callIndex: Int,
    defaultMaskRegister: Int,
): Boolean {
    for (i in callIndex - 1 downTo 0) {
        val instruction = instructions.elementAt(i)
        if (instruction.opcode !in CONST_OPCODES) continue

        val constant = instruction as? OneRegisterInstruction ?: continue
        if (constant.registerA != defaultMaskRegister) continue

        val literal = (constant as? WideLiteralInstruction)?.wideLiteral ?: continue
        return literal and MAX_LINES_DEFAULTED_FLAG.toLong() != 0L
    }
    return false
}
