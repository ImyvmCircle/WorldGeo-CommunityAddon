# Community Addon — Developer Guide

> **Navigation:** [Index](index.md) | [Commands](commands.md) | **Developer Guide**

---

This guide documents conventions and important caveats for contributors working on the WorldGeo-CommunityAddon mod.

---

## Dependency Management: Local Sources vs. Deployed Artifacts

The project depends on `ImyvmWorldGeo` (and related libraries) which are resolved from a Maven repository at build time. The local source tree under adjacent directories (e.g., a sibling `ImyvmWorldGeo/` project) **is not guaranteed to match the artifact version actually used in a build or on the deployed server**.

**Important rules:**
- Do **not** treat local source files in sibling projects as the canonical API reference.
- When implementing features that call into `ImyvmWorldGeo` APIs (e.g., `PlayerInteractionApi`, `RegionDataApi`), verify the method signatures against the **published artifact** (check `gradle.properties` for the version, then inspect the resolved jar or the Maven repository).
- If you write code based on a local source that disagrees with the deployed artifact, the mod **will fail to load** with a `NoSuchMethodError` or similar at runtime.

**How to check the actual artifact:**
```bash
# Print the resolved artifact path for inspection
./gradlew dependencies --configuration runtimeClasspath | grep imyvm

# Decompile the jar to verify API signatures
jar tf ~/.gradle/caches/.../imyvm-world-geo-*.jar
```

---

## Adding a New Geographic Function Type

When adding a new `GeographicFunctionType` entry:

1. Add the enum value to `domain/model/GeographicFunctionType.kt`.
2. Add a `when` branch in `CommunityRegionScopeMenu.generateMenuTitle()` for the new type's UI title.
3. Add a `when` branch in `CommunityRegionScopeMenuHandler.runExecuteScope()` to handle selection.
4. If the function does not support a "global" (all-scopes-at-once) variant, suppress the global button in `CommunityRegionScopeMenu` the same way `SCOPE_DELETION` and `SCOPE_TRANSFER` do.

---

## Adding a New Pending Operation Type

Pending operations are stored in `WorldGeoCommunityAddon.pendingOperations: HashMap<Int, PendingOperation>` keyed by the **source region ID**.

1. Add a new `PendingOperationType` value with the next sequential int in `domain/model/PendingOperation.kt`.
2. Add a companion data class (e.g., `XxxConfirmationData`) if structured data is needed.
3. Add a nullable field for it on the `PendingOperation` data class.
4. Update `addPendingOperation()` in `PendingApplication.kt` to accept and pass through the new field.
5. Add the expiry message handler in `handleExpiredOperation()`.
6. Only `inviterUUID`, `inviteeUUID`, and `creationData` are persisted across server restarts — all other fields are intentionally transient (the 5-minute expiry window makes persistence unnecessary).

---

## Community List — Hidden Variants

`CommunityListMenu` is the standard paginated community browser. When you need a filtered subset (e.g., "all communities except the current one"), **do not modify** `CommunityListMenu` itself. Instead, create a new file that replicates the pagination layout with a custom filter and an `onCommunitySelected` callback. See `ScopeTransferTargetListMenu.kt` as the reference implementation.

---

## Translation Keys

All user-facing strings go through `Translator.tr(key, vararg args)`. Placeholders use `{0}`, `{1}`, `{2}`, etc.

- Chinese: `src/main/resources/assets/community/lang/zh_cn.json`
- English: `src/main/resources/assets/community/lang/en_us.json`

Always add keys to **both** files. Group related keys together (e.g., all `community.scope_transfer.*` keys near `community.scope_delete.*` keys).

> **Single-quote escaping:** Any translation value containing a single quote (e.g., `it''s`, `don''t`) that is called with arguments (`{0}` placeholders) **must** escape the single quote as `''` (two single quotes). This is a `java.text.MessageFormat` requirement; entries without arguments are unaffected.

> **Message format:** Player-facing messages should use MOTD formatting (colours, bold, underline) for visual polish. Do not introduce Unicode special characters. Enum values and similar variables should be converted to human-readable text (e.g., `manor`, `rectangle`) before being inserted into messages. Never wrap argument placeholders in single quotes, as this prevents them from being displayed.

> **Translated names:** Proper nouns and specialised terms must be cross-checked against existing documentation. When unsure, ask — do not guess.

---

## Configuration

- **`CommunityConfig`**: Stores all non-pricing configuration for this mod. Every concrete value must be written here.
- **`PricingConfig`**: Stores all pricing-related configuration (creation cost, join fee, area pricing coefficients, permission pricing coefficients, etc.). Any new pricing coefficient must be written here.

---

## Database Persistence

`CommunityDatabase` maintains all persistence. Whenever a `Community` member variable is modified, **always check** whether the database storage logic also needs updating.

---

## Pending Operations and Confirmation Flows

Any operation that requires a timed response uses `PendingOperation`, managed centrally by `PendingApplication`. Every `PendingOperation` **must** be created through `pendingApplication` — never bypass it.

---

## Community Permission Checks

For any functionality involving community permissions, verify that the relevant entry point is linked to `AdministrationPermission(s)` and `PermissionCheck`.

**For any community administrative operation, both of the following must be called:**
- `canExecuteAdministration` (checks role and privilege)
- `canExecuteOperationInProto` (checks community status)

Neither may be omitted. Use the implementation of `runAdmRegion` (`executeWithPermission` + two-step check) as the canonical reference.

---

## Menu Development Rules

- **Closing the screen:** When a button click needs to send a text message to the player, close the screen first — otherwise the player cannot see the message. Plan the flow carefully to ensure subsequent menus remain reachable.
- **`runBack` logic:** Every new Menu **must** include complete `runBack` logic.
- **List menus:** `AbstractListMenu` is the default implementation for all paginated list menus. New list menus must extend it.
- **Button implementation separation:** Functional parameters inside `addButton(){}` in any Menu under `entrypoint/screen` **must not** be implemented inline. All logic must live in the corresponding feature module under `application/screen`. Check whether the target module file already exists before implementing.
- **Player lists:** All player lists default to rendering the player's verified (online/Mojang) skin head. See `CommunityMemberListMenu` for the reference implementation.
- **Toggle switches:** See the Selection Mode toggle in `MainMenu` and its corresponding Handler as the canonical pattern (i.e., mutate data and re-render the current page).

---

## Command Registration

All commands are registered in `CommandRegister.register()`. Parameter extraction and application-layer dispatch also live in `CommandRegister`. Command parameters **must not** use internal IDs (e.g., `regionNumberId`, `announcementId`) as the sole human-typed argument — they are not human-readable. If such IDs are truly necessary, provide a SuggestionProvider following the existing patterns.

> **Non-ASCII name quoting rule:** Any `SuggestionProvider` for Region, GeoScope, or Community names must wrap non-ASCII-alphanumeric names in double quotes before suggesting them: `if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)`.

---

## Money Handling

All monetary operations use `EconomyMod`. The internal unit is `Long`. Convert to player-readable format by dividing by 100 and formatting to two decimal places (`"%.2f".format(amount / 100.0)`).

---

## Miscellaneous Rules

- In principle, do not create new classes. Do not add code comments.
- After changing any game mechanism, **always** update `README.md`. Focus on player-facing descriptions; do not over-expose implementation details.
- Tests must include `./gradlew runServer`.
- Do not use git.
- When a mechanism, translation key name, or behaviour is unclear, ask the operator. Do not stop the conversation just to confirm requirements, and do not guess.
- This project collaborates closely with IMYVMWorldGeo Core — cross-reference it as needed.
