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
