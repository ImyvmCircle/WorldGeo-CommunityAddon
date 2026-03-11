# WorldGeo-CommunityAddon Project Analysis

## 1. OVERALL PACKAGE/DIRECTORY STRUCTURE (.kt files)

### Directory Tree Summary
```
/src/main/kotlin/com/imyvm/community/
├── WorldGeoCommunityAddon.kt                          [Entry point]
├── domain/                                            [Domain models]
│   ├── model/
│   │   ├── Community.kt
│   │   ├── MemberAccount.kt
│   │   ├── PendingOperation.kt
│   │   ├── GeographicFunctionType.kt
│   │   └── community/
│   │       ├── Announcement.kt
│   │       ├── CommunityListFilterType.kt
│   │       ├── CommunityMessage.kt
│   │       ├── CommunityStatus.kt
│   │       ├── CommunityJoinPolicy.kt
│   │       └── MemberRoleType.kt
│   └── policy/
│       ├── territory/
│       │   ├── TerritoryConfirmationMessage.kt
│       │   └── TerritoryPricing.kt
│       └── permission/
│           ├── CommunityPermissionPolicy.kt
│           ├── AdminPrivilege.kt
│           ├── AdminPrivileges.kt
│           ├── CommunityStatusCheck.kt
│           ├── PermissionResult.kt
├── application/                                       [Business logic]
│   ├── event/
│   │   └── PendingApplication.kt
│   ├── helper/
│   │   └── RefundNotCreated.kt
│   └── interaction/
│       ├── command/
│       │   ├── OperatorCommandHandler.kt
│       │   ├── InformationDisplayCommandHandler.kt
│       │   └── AnnouncementCommandHandler.kt
│       ├── common/
│       │   ├── ChatRoomHandler.kt
│       │   ├── CommunityRegionInteractionHandler.kt
│       │   ├── InformationDisplayApp.kt
│       │   ├── CommunityCreationHandler.kt
│       │   ├── MemberManagementHandler.kt
│       │   ├── ScopeModificationHandler.kt
│       │   ├── ChatChannelManager.kt
│       │   └── helper/
│       │       ├── MemberShipChecker.kt
│       │       ├── CreationHelper.kt
│       │       └── ModificationHelper.kt
│       └── screen/
│           ├── CommunityMenuOpener.kt
│           ├── ConfirmMenuHandler.kt
│           ├── helper/ScreenCreationError.kt
│           ├── outer_community/
│           │   ├── GeoScopeOperationHandler.kt
│           │   ├── CommunityListMenuHandler.kt
│           │   ├── CommunityCreationMenuHandler.kt
│           │   ├── TerritoryMenuHandler.kt
│           │   ├── CommunityCreationSelectionMenuHandler.kt
│           │   └── MainMenuHandler.kt
│           ├── inner_community/
│           │   ├── CommunityMenuHandler.kt
│           │   ├── AdministrationMenuHandler.kt
│           │   ├── CommunitySettingMenuHandler.kt
│           │   ├── InvitationMenuHandler.kt
│           │   ├── chat/ChatMenuHandler.kt
│           │   ├── affairs/
│           │   │   ├── AnnouncementHandler.kt
│           │   │   └── AssetsHandler.kt
│           │   ├── administration_only/
│           │   │   ├── AdministrationAuditMenuHandler.kt
│           │   │   ├── AdministrationTeleportationPointMenuHandler.kt
│           │   │   ├── AdminPrivilegeHandler.kt
│           │   └── multi_parent/
│           │       ├── CommunityRegionScopeMenuHandler.kt
│           │       ├── CommunityMemberListHandler.kt
│           │       ├── ScopeCreationSelectionMenuHandler.kt
│           │       └── element/
│           │           ├── TargetSettingMenuHandler.kt
│           │           └── CommunityMemberMenuHandler.kt
├── entrypoint/                                        [UI & External APIs]
│   ├── DataLoadSaveRegister.kt
│   ├── command/
│   │   ├── CommandRegister.kt
│   │   └── helper/
│   │       ├── CommandArgumentProvider.kt
│   │       └── IdentifierCommunityGetter.kt
│   ├── event/
│   │   ├── PendingCheck.kt
│   │   ├── MailNotification.kt
│   │   ├── AnnouncementNotification.kt
│   │   └── ChatInterceptor.kt
│   └── screen/
│       ├── AbstractListMenu.kt
│       ├── AbstractMenu.kt
│       ├── AbstractRenameMenuAnvil.kt
│       ├── ConfirmMenu.kt
│       ├── component/
│       │   ├── PlayerHeadButton.kt
│       │   ├── ConfirmTaskType.kt
│       │   ├── MenuButton.kt
│       │   ├── ReadOnlySlot.kt
│       │   └── LoreButton.kt
│       ├── outer_community/
│       │   ├── CommunityCreationSelectionMenu.kt
│       │   ├── CommunityScopeSelectionMenu.kt
│       │   ├── MainMenu.kt
│       │   ├── TerritoryMenu.kt
│       │   ├── NonMemberMenus.kt
│       │   ├── CommunityCreationRenameMenuAnvil.kt
│       │   ├── CommunityListMenu.kt
│       │   ├── CommunityCreationMenu.kt
│       │   └── MyCommunityListMenu.kt
│       ├── inner_community/
│       │   ├── CommunityMenu.kt
│       │   ├── CommunityAdministrationMenu.kt
│       │   ├── OnlinePlayerListMenu.kt
│       │   ├── ChatRoomMenu.kt
│       │   ├── affairs/
│       │   │   ├── annoucement/
│       │   │   │   ├── MemberAnnouncementListMenu.kt
│       │   │   │   └── MemberAnnouncementDetailsMenu.kt
│       │   │   ├── CommunitySettingMenu.kt
│       │   │   └── assets/
│       │   │       ├── DonorListMenu.kt
│       │   │       ├── CommunityAssetsMenu.kt
│       │   │       ├── DonationMenu.kt
│       │   │       └── DonorDetailsMenu.kt
│       │   ├── administration_only/
│       │   │   ├── AdministrationTeleportPointMenu.kt
│       │   │   ├── AdministrationAdvancementMenu.kt
│       │   │   ├── AdministrationAuditMenu.kt
│       │   │   ├── NotificationMenuAnvil.kt
│       │   │   ├── AdminPrivilegeMenu.kt
│       │   │   ├── AdministrationRenameMenuAnvil.kt
│       │   │   ├── AdministrationAuditListMenu.kt
│       │   │   └── annoucement/
│       │   │       ├── AdministrationAnnouncementListMenu.kt
│       │   │       ├── AdministrationAnnouncementDetailsMenu.kt
│       │   │       └── AdministrationAnnouncementInputMenuAnvil.kt
│       │   └── multi_parent/
│       │       ├── CommunityRegionScopeMenu.kt
│       │       ├── CommunityScopeCreationRenameMenuAnvil.kt
│       │       ├── CommunityRegionGlobalGeometryMenu.kt
│       │       ├── CommunityScopeCreationMenu.kt
│       │       ├── CommunityMemberListMenu.kt
│       │       └── element/
│       │           ├── CommunityMemberMenu.kt
│       │           └── TargetSettingMenu.kt
├── infra/                                             [Configuration & Data]
│   ├── CommunityDatabase.kt
│   ├── CommunityConfig.kt
│   └── PricingConfig.kt
└── util/                                              [Utilities]
    ├── TextParser.kt
    ├── FormatMills.kt
    ├── Translator.kt
    └── MailCarrier.kt
```

**Total Kotlin Files: 110+ files**

---

## 2. DEVELOPMENT_GUIDELINES.md CONTENT

**Location:** `/home/doohaer/Minecraft/IMYVM/src/WorldGeo-CommunityAddon/.github/agents/instructions/DEVELOPMENT_GUIDELINES.md`

### Key Requirements:

1. **i18n System (Translator.tr)**
   - All player-facing text must use `Translator.tr()` function
   - Must implement both Chinese (zh_cn.json) and English (en_us.json) entries
   - Use MOTD format for color/formatting (§c, §l, etc.), no Unicode symbols
   - Don't use single quotes around parameters (breaks display)
   - Use human-readable text for enum values (e.g., "manor" not "MANOR")

2. **Configuration**
   - Non-pricing configs → `CommunityConfig`
   - All pricing coefficients (creation, joining, area costs, permission costs) → `PricingConfig`

3. **Database Operations**
   - `CommunityDatabase` handles Community member variable modifications
   - Any Community state change requires checking database implications

4. **Pending Operations**
   - Use `PendingOperation` for time-limited confirmations
   - Must use centralized `pendingApplication` manager
   - Used for confirmation logic across the mod

5. **Member Roles**
   - Formal members: OWNER, ADMIN, MEMBER (default when discussing membership)
   - APPLICANT and REFUSED are NOT formal members

6. **Permission Checking (CRITICAL REQUIREMENT)**
   - **ALL admin operations MUST check BOTH:**
     - `canExecuteAdministration()` - checks role and permissions
     - `canExecuteOperationInProto()` - checks community status
   - Pattern: Use `executeWithPermission` + two-step checks (see `runAdmRegion`)
   - **Missing either check is considered a bug**

7. **Menu/Screen Design**
   - Close panels before displaying text messages to players
   - Plan flow carefully (avoid unreachable menus)
   - Implement complete `runBack` logic for all menus
   - Use `AbstractListMenu` for list implementations

8. **Handler Pattern**
   - Menu `addButton()` onClick callbacks are in `/entrypoint/screen/`
   - Actual implementations go in `/application/interaction/screen/` 
   - Verify handler file exists before adding buttons

9. **Command Registration**
   - All commands registered in `CommandRegister.register()`
   - Avoid using raw IDs (regionNumberId, announcementId) - use Providers instead

10. **Player Lists**
    - Default support for player head skulls (see `CommunityMemberListMenu`)

11. **Toggle Switches**
    - Standard pattern: modify data → reload page (see MainMenu Selection Mode)

12. **Money Operations**
    - EconomyMod integration, unit = Long
    - Display: divide by 100, format to 2 decimals (e.g., "$12.34")

13. **General Rules**
    - Don't create new classes unnecessarily
    - No comments needed
    - Update README.md when mechanics change (player-facing descriptions)
    - No git usage
    - Ask when unclear about mechanics or language keys

---

## 3. REGION/SCOPE NAME USAGE PATTERNS

### A. How Names Are Passed as Arguments

**Community Creation (via Anvil):**
- File: `CommunityCreationRenameMenuAnvil.kt` (lines 10-29)
- Anvil captures name → passes to `CommunityCreationSelectionMenu` constructor
- Name flows through: Anvil → Selection Menu → Creation Menu

```kotlin
// CommunityCreationRenameMenuAnvil.kt:21-24
override fun processRenaming(finalName: String) {
    CommunityMenuOpener.open(playerExecutor) { newSyncId ->
        CommunityCreationSelectionMenu(newSyncId, finalName, isManor, playerExecutor, runBackGrandfather)
    }
}
```

**Scope Creation (via Anvil):**
- File: `CommunityScopeCreationRenameMenuAnvil.kt` (lines 19-23)
- Similar pattern: anvil captures name → passes to `CommunityScopeCreationMenu`

```kotlin
override fun processRenaming(finalName: String) {
    CommunityMenuOpener.open(playerExecutor) { newSyncId ->
        CommunityScopeCreationMenu(newSyncId, community, finalName, playerExecutor, runBackGrandfather)
    }
}
```

### B. Names Parsed from Commands

**File:** `CommandRegister.kt`
- Community creation command parameter: community name as String argument
- Passed to `CommunityCreationHandler` which validates:
  - Empty check → error key: "ui.create.error.name_empty"
  - Duplicate check → error key: "ui.create.error.name_duplicated"

### C. Names Entered via GUI

**Community Name Input (Anvil GUI):**
- File: `AbstractRenameMenuAnvil.kt` (lines ~25-30)
- Anvil screen with NAME_TAG item
- Initial name set via `DataComponentTypes.CUSTOM_NAME`
- Player edits name in anvil text field
- On anvil confirm, calls `processRenaming(finalName: String)`

**Scope Name Input (Anvil GUI):**
- Same anvil pattern, different handler
- Default scope name: `Translator.tr("ui.admin.region.global.add.default_name")` = "New-District"
- Can be renamed before creation (button slot 28 in `CommunityScopeCreationMenu`)

**Scope Selection via Chest GUI:**
- File: `CommunityRegionScopeMenu.kt` (lines 72-78, 90-95)
- Displays list of scopes with `scope.scopeName` as button names
- Scopes retrieved from: `community.getRegion()?.geometryScope`
- Each scope has `.scopeName` property (String field in GeoScope from IWG)

---

## 4. ANVIL RENAME GUI IMPLEMENTATION - FULL CONTENT

### Base Class: AbstractRenameMenuAnvil.kt

**Location:** `/home/doohaer/Minecraft/IMYVM/src/WorldGeo-CommunityAddon/src/main/kotlin/com/imyvm/community/entrypoint/screen/AbstractRenameMenuAnvil.kt`

Abstract class that all rename anvils extend. Key pattern:
1. Opens AnvilScreenHandler
2. Sets initial name in NAME_TAG (input slot 0)
3. Player edits the name
4. On confirm, extracts new name and calls `processRenaming(finalName: String)`

---

### Implementation 1: CommunityCreationRenameMenuAnvil.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/outer_community/CommunityCreationRenameMenuAnvil.kt`

```kotlin
class CommunityCreationRenameMenuAnvil(
    val playerExecutor: ServerPlayerEntity,
    initialName: String,
    private val currentShape: GeoShapeType,
    private val isManor: Boolean,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit)
) : AbstractRenameMenuAnvil(playerExecutor, initialName) {

    override fun processRenaming(finalName: String) {
        // Pass name to next menu
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityCreationSelectionMenu(
                newSyncId, 
                finalName,           // ← Renamed community name
                isManor, 
                playerExecutor, 
                runBackGrandfather
            )
        }
    }

    override fun getMenuTitle(): Text = 
        Translator.tr("ui.create.rename.title") ?: Text.of("Rename Community")
}
```

---

### Implementation 2: AdministrationRenameMenuAnvil.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/inner_community/administration_only/AdministrationRenameMenuAnvil.kt`

```kotlin
class AdministrationRenameMenuAnvil(
    player: ServerPlayerEntity,
    private val community: Community,
    private val scopeName: String?,        // null = rename community, non-null = rename scope
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit)
) : AbstractRenameMenuAnvil(
    player = player,
    initialName = if (scopeName == null) {
        // Get community (region) name from IWG API
        community.regionNumberId?.let { RegionDataApi.getRegion(it)?.name } 
            ?: "Unknown Name"
    } else {
        scopeName  // Use existing scope name
    }
) {
    override fun processRenaming(finalName: String) {
        val regionId = community.regionNumberId ?: return
        val nameKey = scopeName ?: "global"  // "global" = community-level rename
        val cost = if (scopeName == null) 
            PricingConfig.RENAME_GLOBAL_COST.value 
        else 
            PricingConfig.RENAME_SCOPE_COST.value

        // Check rename cooldown (30 days)
        val cooldownMs = community.nameChangeCooldowns[nameKey] ?: 0L
        val daysSince = (System.currentTimeMillis() - cooldownMs) / (1000L * 60 * 60 * 24)
        if (daysSince < 30) {
            val daysLeft = 30 - daysSince
            player.closeHandledScreen()
            player.sendMessage(
                Translator.tr("community.rename.error.cooldown", 
                    daysLeft.toString(), 
                    nameKey
                )
            )
            return
        }

        // Check for existing pending rename operation
        val existingPending = WorldGeoCommunityAddon.pendingOperations[regionId]
        if (existingPending != null) {
            player.closeHandledScreen()
            player.sendMessage(
                Translator.tr("community.modification.confirmation.pending")
            )
            return
        }

        // Check assets for rename cost
        val currentAssets = community.getTotalAssets()
        if (cost > 0 && currentAssets < cost) {
            player.closeHandledScreen()
            player.sendMessage(
                Translator.tr(
                    "community.modification.error.insufficient_assets",
                    String.format("%.2f", cost / 100.0),
                    String.format("%.2f", currentAssets / 100.0)
                )
            )
            return
        }

        // Create pending operation
        addPendingOperation(
            regionId = regionId,
            type = PendingOperationType.RENAME_CONFIRMATION,
            expireMinutes = 5,
            renameData = RenameConfirmationData(
                regionNumberId = regionId,
                nameKey = nameKey,
                newName = finalName,
                executorUUID = player.uuid,
                cost = cost
            )
        )

        player.closeHandledScreen()
        sendInteractiveRenameConfirmation(player, regionId, nameKey, finalName, cost)
    }

    private fun sendInteractiveRenameConfirmation(
        player: ServerPlayerEntity, 
        regionNumberId: Int, 
        nameKey: String, 
        newName: String, 
        cost: Long
    ) {
        val costDisplay = String.format("%.2f", cost / 100.0)
        player.sendMessage(
            Translator.tr("community.rename.bill", nameKey, newName, costDisplay)
        )

        // Confirm button
        val confirmButton = Text.literal("§a§l[确认]§r")
            .styled { style ->
                style.withClickEvent(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/community confirm_rename $regionNumberId $nameKey"
                    )
                ).withHoverEvent(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Translator.tr("community.rename.confirm.hover") 
                            ?: Text.literal("Click to confirm rename")
                    )
                )
            }

        // Cancel button
        val cancelButton = Text.literal("§c§l[取消]§r")
            .styled { style ->
                style.withClickEvent(
                    ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/community cancel_rename $regionNumberId $nameKey"
                    )
                ).withHoverEvent(
                    HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Translator.tr("community.rename.cancel.hover") 
                            ?: Text.literal("Click to cancel rename")
                    )
                )
            }

        val promptMessage = Text.empty()
            .append(Text.literal("§e§l[等待确认]§r §e请在 §c§l5分钟§r§e 内操作: "))
            .append(confirmButton)
            .append(Text.literal(" "))
            .append(cancelButton)

        player.sendMessage(promptMessage)
    }

    override fun getMenuTitle(): Text {
        return if (scopeName == null) {
            Translator.tr("ui.admin.rename.title") ?: Text.of("Rename Community")
        } else {
            Translator.tr("ui.admin.rename.scope.title", scopeName) 
                ?: Text.of("Rename Scope: $scopeName")
        }
    }
}
```

### Implementation 3: CommunityScopeCreationRenameMenuAnvil.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/inner_community/multi_parent/CommunityScopeCreationRenameMenuAnvil.kt`

```kotlin
class CommunityScopeCreationRenameMenuAnvil(
    private val playerExecutor: ServerPlayerEntity,
    private val community: Community,
    initialName: String,
    private val runBackGrandfather: (ServerPlayerEntity) -> Unit
) : AbstractRenameMenuAnvil(playerExecutor, initialName) {
    
    override fun processRenaming(finalName: String) {
        CommunityMenuOpener.open(playerExecutor) { newSyncId ->
            CommunityScopeCreationMenu(
                newSyncId, 
                community, 
                finalName,  // ← Renamed scope name
                playerExecutor, 
                runBackGrandfather
            )
        }
    }

    override fun getMenuTitle(): Text {
        return Translator.tr("ui.admin.region.global.add.rename.title")
            ?: Text.of("Rename Administrative District")
    }
}
```

---

## 5. CHEST GUI MENUS FOR REGION/SCOPE SELECTION

### Primary Scope Selection Menu: CommunityRegionScopeMenu.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/inner_community/multi_parent/CommunityRegionScopeMenu.kt`

**Lines 68-99:** Lists all scopes with their names as button labels

```kotlin
private fun addLocalButtonsForPage0() {
    val scopes = community.getRegion()?.geometryScope ?: return
    val scopesInPage = scopes.take(unitsInPageZero)
    
    renderList(scopesInPage, unitsInPageZero, startSlotInPageZero) { scope, slot, _ ->
        val item = getScopeItemBySlot(slot)
        addButton(
            slot = slot,
            name = scope.scopeName,    // ← Scope name used as button label
            item = item
        ) { runExecuteScope(playerExecutor, community, scope, geographicFunctionType, playerObject, runBack) }
    }
    
    handlePageWithSize(scopes.size, unitsPerPage)
}
```

**Key Properties:**
- Menu title dynamically generated based on `geographicFunctionType`
- Scopes have colored icons based on slot position
- Supports pagination (35 scopes per page)
- Shows "Global" button for region-level operations

**GeographicFunctionType options:**
- `GEOMETRY_MODIFICATION` - Modify scope boundaries
- `SETTING_ADJUSTMENT` - Change permissions/rules
- `TELEPORT_POINT_LOCATING` - Set teleport points
- `TELEPORT_POINT_EXECUTION` - Teleport to scope
- `NAME_MODIFICATION` - Rename scope

---

### Scope Creation Menu: CommunityScopeCreationMenu.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/inner_community/multi_parent/CommunityScopeCreationMenu.kt`

Displays current scope creation state with:
- Scope name button (editable, slot 28) - shows `currentName`
- Shape selector
- Selection mode toggle
- Confirm button

**Lines 84-88:**
```kotlin
if (hasEnoughPoints) {
    addButton(
        slot = 28,
        name = currentName,    // ← Current scope name display
        item = Items.NAME_TAG
    ) { runRenameNewScopeFromSelection(it, community, currentName, runBack) }
}
```

---

### Community Selection Menu: CommunityScopeSelectionMenu.kt

**Location:** `/src/main/kotlin/com/imyvm/community/entrypoint/screen/outer_community/CommunityScopeSelectionMenu.kt`

Displays list of communities for scope selection:
- Shows community mark/name (from `community.generateCommunityMark()`)
- Player head icons for community owners
- Used for: scope modification, teleport, region operations

---

## 6. HOW COMMUNADDON DEPENDS ON IMYVMWORLDGEO (IWG)

### IWG API Entry Points

**Data Classes Imported from IWG:**
- `com.imyvm.iwg.domain.component.GeoShapeType` - RECTANGLE, CIRCLE, POLYGON
- `com.imyvm.iwg.domain.component.HypotheticalShape` - Selection state during point placing
- `com.imyvm.iwg.domain.component.GeoScope` - Scope object with `scopeName`, geometry data
- `com.imyvm.iwg.ImyvmWorldGeo` - Main IWG entry point

### API Calls Made from CommunityAddon

#### 1. RegionDataApi - READ operations

**Location:** Used in multiple handler files

```kotlin
// Get region by ID
val region = RegionDataApi.getRegion(regionNumberId)
region?.name  // Get community name

// Get region list
val regions = RegionDataApi.getRegionList(world)

// Check teleport point accessibility
val isPublic = RegionDataApi.inquireTeleportPointAccessibility(scope)
```

**Files using RegionDataApi:**
- `AdministrationRenameMenuAnvil.kt:23` - Get community name from region
- `AdministrationTeleportPointMenu.kt` - Check teleport accessibility
- `OperatorCommandHandler.kt` - Get region to delete
- `IdentifierCommunityGetter.kt` - Get region list for commands

#### 2. PlayerInteractionApi - WRITE operations

**Location:** Used in handlers and commands

```kotlin
// Point selection (圈地模式)
PlayerInteractionApi.startSelection(player, GeoShapeType.RECTANGLE)
PlayerInteractionApi.stopSelection(player)
PlayerInteractionApi.resetSelection(player)
PlayerInteractionApi.setSelectionShape(player, newShape)

// Teleport point management
PlayerInteractionApi.addTeleportPoint(player, region, scope)
PlayerInteractionApi.resetTeleportPoint(player, region, scope)
PlayerInteractionApi.getTeleportPoint(scope)  // Returns BlockPos or null
PlayerInteractionApi.toggleTeleportPointAccessibility(scope)
PlayerInteractionApi.teleportPlayerToScope(player, region, scope)

// Scope management (via ScopeModificationHandler)
PlayerInteractionApi.addScope(player, region, scopeName)
PlayerInteractionApi.modifyScope(player, region, scopeName)
PlayerInteractionApi.deleteRegion(player, region)

// Information
PlayerInteractionApi.queryRegionInfo(player, region)
```

**Files using PlayerInteractionApi:**
- `CommandRegister.kt` - Start/stop/reset selection
- `MainMenuHandler.kt` - Toggle selection mode
- `CommunityCreationSelectionMenuHandler.kt` - Shape switching
- `AdministrationTeleportationPointMenuHandler.kt` - Teleport management
- `ScopeModificationHandler.kt` - Add/modify/delete scopes
- `CommunityMenuHandler.kt` - Teleport execution
- `OperatorCommandHandler.kt` - Delete region, query info

#### 3. ImyvmWorldGeo - Point selection state tracking

**Location:** Used to check if player is in selection mode

```kotlin
// Get current selection state
val selectionState = ImyvmWorldGeo.pointSelectingPlayers[playerExecutor.uuid]
val hypotheticalShape = selectionState?.hypotheticalShape
val points = selectionState?.points

// Check if currently modifying
if (hypotheticalShape is HypotheticalShape.ModifyExisting) {
    val scope = hypotheticalShape.scope  // Get being-modified scope
    val scopeName = scope.scopeName      // Get its name
}
```

**Files using ImyvmWorldGeo:**
- `CommunityCreationSelectionMenu.kt` - Show current shape/points
- `CommunityScopeCreationMenu.kt` - Track scope creation progress
- `CommunityRegionScopeMenu.kt` - Show modifying hint in title
- `TerritoryMenu.kt` - Show creation/modification status

### Data Flow: Region ↔ Community

```
Community (in CommunityAddon)
├─ regionNumberId: Int  ← Links to IWG Region
├─ getRegion(): Region  ← Calls RegionDataApi.getRegion(regionNumberId)
└─ nameChangeCooldowns: Map<String, Long>

    ↓ (via RegionDataApi)

Region (in IMYVMWorldGeo)
├─ numberID: Int
├─ name: String  ← Community display name
└─ geometryScope: List<GeoScope>
    ├─ GeoScope #1
    │  ├─ scopeName: String  ← "辖区1", "District A", etc.
    │  ├─ geometry: Shape
    │  └─ ...
    └─ GeoScope #2
       ├─ scopeName: String
       └─ ...
```

### Name Usage Pattern

1. **Community Name (Global):**
   - Stored in: `Region.name` (IWG)
   - Renamed via: `AdministrationRenameMenuAnvil` with `scopeName=null`
   - Displayed in: community menus, lists, notifications

2. **Scope Name (Local):**
   - Stored in: `GeoScope.scopeName` (IWG)
   - Renamed via: `AdministrationRenameMenuAnvil` with `scopeName="existing_scope_name"`
   - Displayed in: scope selection menu, scope info
   - Retrieved from: `community.getRegion()?.geometryScope`

---

## 7. I18N/LANG FILES - VALIDATION-RELATED KEYS

### Language Files Location

**Chinese:** `/src/main/resources/assets/community/lang/zh_cn.json`
**English:** `/src/main/resources/assets/community/lang/en_us.json`

### Name Validation Keys

#### Community/Scope Creation Validation

**Line 113-116 (zh_cn.json):**
```json
"ui.create.error.name_empty": "名称不能为空。",
"ui.create.error.name_duplicated": "名称已被占用。",
"ui.create.error.shape_unknown": "请选择一个形状。",
"ui.create.error.shape_polygon": "多边形至少需要3个点。",
"ui.create.error.shape_not_enough": "请至少选择2个点。",
```

**Lines 113-116 (en_us.json):**
```json
"ui.create.error.name_empty": "Name cannot be empty.",
"ui.create.error.name_duplicated": "Name is already taken.",
"ui.create.error.shape_unknown": "Please select a shape.",
"ui.create.error.shape_polygon": "Polygon requires at least 3 points.",
"ui.create.error.shape_not_enough": "Please select at least 2 points.",
```

#### Rename Operation Validation

**Lines 639-649 (zh_cn.json):**
```json
"community.rename.error.cooldown": "§c§l[冷却中]§r §c「{1}」的名称在 §4§l{0}天§r §c后才能再次修改。",
"community.rename.error.scope_not_found": "§c§l[错误]§r §c未找到 Geoscope「{0}」，可能已被删除。",
"community.rename.bill": "§6§l[改名账单]§r §6对象：§f§l「{0}」§r §6→ §a§l「{1}」§r §6，费用：§a§l${2}§r§6。",
"community.rename.confirm.hover": "§a>>> §l点击确认§r §a改名 <<<",
"community.rename.cancel.hover": "§c>>> §l点击取消§r §c改名 <<<",
"community.rename.success.global": "§a§l[改名成功]§r §a聚落名称已从 §f§l「{0}」§r §a改为 §f§l「{1}」§r§a。",
"community.rename.success.scope": "§a§l[改名成功]§r §a地域名称已从 §f§l「{0}」§r §a改为 §f§l「{1}」§r§a。",
"community.rename.cancelled": "§7§l[已取消]§r §7改名操作已取消。",
```

**Lines 639-649 (en_us.json):**
```json
"community.rename.error.cooldown": "§c§l[Cooldown]§r §c「{1}」 name can be changed again in §4§l{0} day(s)§r§c.",
"community.rename.error.scope_not_found": "§c§l[Error]§r §cGeoscope「{0}」 not found, it may have been deleted.",
"community.rename.bill": "§6§l[Rename Bill]§r §6Target: §f§l「{0}」§r §6→ §a§l「{1}」§r §6, Cost: §a§l${2}§r§6.",
"community.rename.confirm.hover": "§a>>> §lClick to confirm§r §arename <<<",
"community.rename.cancel.hover": "§c>>> §lClick to cancel§r §crename <<<",
"community.rename.success.global": "§a§l[Rename Success]§r §aCommunity name changed from §f§l「{0}」§r §ato §f§l「{1}」§r§a.",
"community.rename.success.scope": "§a§l[Rename Success]§r §aScope name changed from §f§l「{0}」§r §ato §f§l「{1}」§r§a.",
"community.rename.cancelled": "§7§l[Cancelled]§r §7Rename operation cancelled.",
```

#### Asset/Cost Validation

**Lines 497-498 (zh_cn.json):**
```json
"community.modification.error.insufficient_assets": "§c§l[国库不足]§r §c需要 §e{0}§c，但只有 §e{1}§c。",
"community.modification.confirmation.pending": "§e§l[待处理]§r §e您已有一个待处理的地理范围确认。",
```

**Lines 497-498 (en_us.json):**
```json
"community.modification.error.insufficient_assets": "§c§l[INSUFFICIENT ASSETS]§r §cNeed §e{0}§c, but only have §e{1}§c.",
"community.modification.confirmation.pending": "§e§l[PENDING]§r §eYou already have a pending geographic scope confirmation.",
```

#### Scope Validation

**Line 527 (zh_cn.json):**
```json
"community.scope_add.error.scope_limit_exceeded": "§c§l[已达上限]§r §c最大辖区数：§e{0}§c（正式成员：§e{1}§c，当前辖区数：§e{2}§c）。",
```

**Line 527 (en_us.json):**
```json
"community.scope_add.error.scope_limit_exceeded": "§c§l[LIMIT EXCEEDED]§r §cMax districts: §e{0}§c (formal members: §e{1}§c, current districts: §e{2}§c).",
```

#### Modification Errors

**Lines 492-496 (zh_cn.json):**
```json
"community.modification.error.no_region": "§c§l[错误]§r §c未找到此领域的地块。",
"community.modification.error.insufficient_points": "§c§l[错误]§r §c修改所选点不足。",
"community.modification.error.duplicated_points": "§c§l[错误]§r §c选择中检测到重复点。",
"community.modification.error.coincident_points": "§c§l[错误]§r §c选择中检测到重合点。",
"community.modification.error.overlap_detected": "§c§l[错误]§r §c检测到与其他辖区范围重叠。",
"community.modification.error.unknown": "§c§l[错误]§r §c未知修改错误。",
```

---

## Summary of Key Points

✅ **Name Flow:**
- Anvil input → Menu parameter → Handler → Validation → Storage (via IWG API)

✅ **Scope Names Storage:**
- In GeoScope objects (IWG data structure)
- Retrieved via `community.getRegion()?.geometryScope`
- Displayed in `CommunityRegionScopeMenu` and chest GUIs

✅ **IWG Integration:**
- RegionDataApi for reads
- PlayerInteractionApi for writes
- ImyvmWorldGeo for tracking selection state

✅ **Validation:**
- Empty name check
- Duplicate name check
- Cooldown check (30 days between renames)
- Assets check (cost validation)
- Pending operation check

✅ **Language Support:**
- All errors have i18n keys
- Both Chinese and English support required
- Format: cost as locale-formatted money (divide by 100, 2 decimals)
