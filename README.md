# Blackout Patches for OpenCode Mobile

Patches for OpenCode Mobile (`dev.opencode.mobile.morphe`).

## How to use

Add this source to Morphe Manager:
https://morphe.software/add-source?github.com/blackout/morphe-patches

## Patches

### Max lines

Lets question option descriptions and tool call headers wrap onto more than one line.

The app renders every string through a single Compose `Text` bridge and hardcodes
`maxLines = 1` at the two call sites that were visibly clipped:

| Text | Effect |
|---|---|
| Question option description (the "detail" under a choice label) | wraps to 5 lines instead of 1 |
| Tool call detail sheet header (the shell command) | wraps to 5 lines instead of 1 |

Option labels, button text and every other deliberately single-line label are left alone.

The value lives in `patches/src/main/kotlin/app/morphe/patches/opencode/text/MaxLinesPatch.kt`
(`MAX_LINES`). Set it to `Int.MAX_VALUE` and change `MAX_LINES_OPCODE` to
`Opcode.CONST` to never clip.

## How it finds the code

R8 renames every class on each app release, so nothing here is fingerprinted by name.

- `TextWrapperFingerprint` matches the shared `Text` bridge purely by its 18-parameter
  shape — the only method in the app with that signature. The patch derives the
  `maxLines` argument register from that wrapper's own parameter types at runtime
  instead of hardcoding register offsets.
- `QuestionOptionDescriptionFingerprint` pins the option-row class via its constructor
  tail `(String, List, String)` (description, options, selected label), then matches
  the composable that renders a label followed by a null-guarded description.
- `ToolDetailSheetHeaderFingerprint` matches the bottom-sheet header builder by
  signature and by reading static float constants declared on its own class.

Each fingerprint was verified to resolve to exactly one method across `classes.dex`
and `classes2.dex` of v1.1.0.

## Development

```bash
./gradlew buildAndroid     # -> patches/build/libs/patches-*.mpp
```

See the [Morphe patcher documentation](https://github.com/MorpheApp/morphe-patcher/blob/main/docs)
for the fingerprinting rules.

## License

GPLv3. See [LICENSE](LICENSE).
