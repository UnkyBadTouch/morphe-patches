package app.morphe.patches.opencode.text

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Parameter list of the app's shared Compose `Text` bridge.
 *
 * The `int boolean int int` run near the end is `overflow, softWrap, maxLines,
 * minLines`. Only `Ljava/lang/String;` is a stable type here — every other
 * reference is R8-obfuscated and changes name between app releases, so it is
 * declared as the bare object type `"L"`.
 */
internal val TextWrapperParameters = listOf(
    "Ljava/lang/String;", // text
    "L",                  // color
    "J", "J",             // fontSize, fontStyle
    "L", "J",             // fontWeight, letterSpacing
    "L", "L",             // textDecoration, textAlign
    "J",                  // lineHeight
    "I",                  // overflow
    "Z",                  // softWrap
    "I",                  // maxLines  <- raised by this patch
    "I",                  // minLines
    "L",                  // modifier
    "L",                  // composer
    "I", "I", "I",        // $changed, $changed1, $default
)

/**
 * The app renders every piece of text through this one Compose `Text` bridge.
 *
 * The wrapper branches on bit `0x4000` of its `$default` mask to decide whether
 * `maxLines` was passed by the caller or defaulted to unlimited. Every text this
 * patch cares about passes an explicit `1`, so that bit is clear and the
 * hardcoded single line wins.
 *
 * Fingerprinted by parameter shape rather than name: R8 renames this class on
 * every release (`z1/w1` in v1.0.0, `z1/v1` in v1.1.0). Matches exactly one
 * method app-wide.
 */
internal object TextWrapperFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = TextWrapperParameters,
    filters = listOf(
        literal(0x4000)
    )
)

/**
 * Question card — the option **description** shown under each choice label.
 *
 * The composable renders two texts: the option label first, then the description.
 * The description is read from a `String` field of this class and null checked
 * before use, so the `iget-object` / `if-eqz` pair between the two calls is what
 * separates them. The label is deliberately left on one line.
 */
internal object QuestionOptionDescriptionFingerprint : Fingerprint(
    classFingerprint = QuestionOptionRowClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        // First call: the option label.
        methodCall(name = "b", parameters = TextWrapperParameters),
        // The description is a String field on this class, and is only rendered if non null.
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/String;"
        ),
        opcode(Opcode.IF_EQZ),
        // Second call: the description. This is the one the patch raises.
        methodCall(name = "b", parameters = TextWrapperParameters),
    )
)

/**
 * Identifies the option-row class without naming it.
 *
 * Its constructor ends in `(String, List, String)` — description, options,
 * selected label. That tail is the only such constructor in the app and survives
 * R8 renaming far better than the full 20-parameter shape.
 */
internal object QuestionOptionRowClassFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf(
        "L", "F", "L", "J", "L", "J", "L", "L", "Z", "Z", "L",
        "Ljava/lang/String;",
        "J", "J", "J", "L", "J",
        "Ljava/lang/String;",
        "Ljava/util/List;",
        "Ljava/lang/String;",
    )
)

/**
 * Tool call detail bottom sheet — the header title, which carries the full shell
 * command. Tapping a tool call opens the `session_tool_detail` sheet; this method
 * builds its heading.
 *
 * Among methods with this signature that render text, this is the only one reading
 * static float fields declared on its own class (the sheet's animation constants).
 * The one other candidate in the app reads no fields of its own.
 */
internal object ToolDetailSheetHeaderFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;", // sheet title: the command text
        "L",                  // callbacks
        "L",
        "L",                  // content color
        "L",                  // composer
        "I",
    ),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET,
            definingClass = "this",
            type = "F"
        ),
        methodCall(name = "b", parameters = TextWrapperParameters),
    )
)
