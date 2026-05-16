# Main Menu

> **Navigation:** [Index](index.md) | **Main Menu** | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

The **Community Main Menu** is the entry point for all community features. Players can open it at any time by running `/community` with no arguments. If a player is already in a selection workflow, `/community` returns to the current creation or modification flow without clearing selected points.

## Opening the Menu

| Method | How |
|---|---|
| Command | `/community` |

The menu is a chest-style GUI containing the following buttons:

| Slot | Icon | Button | Description |
|---|---|---|---|
| 10 | Writable Book | **Browse Communities** | Opens the Community List Menu |
| 13 | Diamond Pickaxe | **圈地 (Territory)** | Opens the Territory Menu for land-claiming operations |
| 16 | Red Bed | **My Communities** | Opens the player's joined community list |
| 19 | Anvil | **OP Functions** | *(Operators only)* Server management tools |
| 44 | Lime/Gray Dye | **Action Bar** | Toggle the region-location action bar display |
| 22 | Command Block | **Close Selection Mode** | *(shown only while selection mode is active)* Stops the current selection |
| 31 | TNT | **Reset Selection** | *(shown only while selection mode is active)* Clears selected points |

---

## 圈地 (Territory)

Clicking **圈地** opens the **Territory Menu** — the hub for all land-claiming operations.

| Slot | Icon | Button | Description |
|---|---|---|---|
| 10 | Diamond Pickaxe | **Create Community (创建聚落)** | Opens the Community Creation screen |
| 13 | Grass Block | **Add Scope (增加辖区)** | Add a new scope to an existing community |
| 16 | Shears | **Modify Territory (修改地块)** | Modify the geometry of an existing scope |

### Create Community (创建聚落)

The **Community Creation Screen** changes its buttons according to whether the player is selecting points:

| Slot | Icon | Button | Action |
|---|---|---|---|
| 10 | Redstone Block/Command Block | **Selection Mode** | Toggle selection mode on/off |
| 12 | Clock/Map/Nether Star | **Shape** | *(visible in selection mode)* Cycle through Rectangle → Polygon → Circle |
| 14 | Ender Pearl | **Exit to Select** | *(visible in selection mode)* Close screen to place points in the world |
| 19 | TNT | **Reset Selection** | *(visible in selection mode)* Clear selected points |
| 28 | Name Tag | **Community Name** | *(visible when ≥2 points)* Open anvil to name the community |
| 31 | Birch/Cherry Planks | **Community Type** | *(visible when ≥2 points)* Toggle Manor / Realm |
| 34 | Emerald Block | **Confirm Creation** | *(visible when ≥2 points)* Submit the creation request |

**How to use:**

1. Click **Create Community** in the Territory Menu.
2. In the creation screen, click **Selection Mode** to enable it. The screen closes.
3. Right-click blocks in the world while holding a **Nether Star** to set boundary points. Left-click the Nether Star to remove the last placed point (undo).
4. Re-open `/community` or **Create Community** from the Territory Menu to return to the screen with the selected points loaded.
5. Optionally switch shape, set a name, choose type, then click **Confirm Creation**.

> **Tip:** When Selection Mode opens, chat also names the dimension where the creation will take place. The Overworld is shown in green, the Nether in red, and the End in a pale gold-white highlight. The creation confirmation keeps that same dimension reminder visible.

> **Tip:** Use `/community select start` and `/community select stop` as command alternatives for toggling selection mode. The command path shows the same current-dimension hint.

### Add Scope (增加辖区)

Opens the scope creation flow:

1. If the player belongs to multiple communities, select which one receives the new scope.
2. Permission is checked. The operation requires **Region Geometry** privilege.
3. The **Scope Creation Screen** opens — same layout as the Community Creation Screen.

> **Warning:** If the player is currently modifying an existing scope, that operation must be completed or cancelled before adding a scope.

> **Tip:** Opening Selection Mode for a new scope also names the dimension that scope is being drawn in, and the later confirmation repeats that dimension so cross-dimensional planning stays clear.

### Modify Territory (修改地块)

Opens the scope modification flow:

1. If Modify mode is already active for a chosen scope and new points have been selected, the modification proceeds to confirmation.
2. If not in selection mode, select a community (if in multiple), then select a scope from the list.
3. Clicking a scope starts **ModifyExisting** selection mode and closes the menu with a prompt.
4. Select new boundary points in the world, then re-open the Territory Menu → **Modify Territory** to confirm.

> **Warning:** If normal creation selection mode is active, the menu closes with a warning to exit that mode first.

> **Note:** Modification can begin only while standing in the **same dimension as the target scope**, and the same rule applies when entering modification confirmation. If the scope is clicked from another dimension, the menu closes, chat explains the mismatch, and a clickable **return** button appears to reopen the previous interface.

> **Note:** Requires the **Region Geometry** admin privilege.

Clicking **Browse Communities** opens the **Community List Menu**, which displays all communities on the server as clickable items. Use the filter buttons at the top to narrow the list:

| Filter | Description |
|---|---|
| **All** | Every community regardless of status |
| **Joinable** | Communities currently open to the player |
| **Recruiting** | Realms actively seeking founding members |
| **Auditing** | Communities pending operator approval |
| **Active** | Fully operational communities |
| **Revoked** | Deactivated communities |

Each entry shows the community name, ID, founding time, status, join policy, and member count. Clicking an entry opens the [Community Menu](community-menu.md) for formal members, or the non-member join view otherwise.

**Command equivalent:** `/community list [all|joinable|recruiting|auditing|active|revoked]`

---

## Create Community

> **See the 圈地 section above** for the integrated creation workflow.

The community creation screen includes selection mode controls directly — no need to set up selection mode from the main menu beforehand.

#### Shape Types

| Shape | Points Needed | How Points Are Interpreted |
|---|---|---|
| **Rectangle** | 2 | Two diagonal corner points |
| **Circle** | 2 | Center point + one point on the circumference |
| **Polygon** | 3+ | All vertices in order (must be convex) |

### Confirm and Pay

After clicking **Confirm Creation**:

- The region is created as a pending creation request.
- A cost summary is shown in chat, including base cost and area surcharge.
- An interactive **[CONFIRM]** / **[CANCEL]** prompt appears in chat. The prompt is valid for **5 minutes**.
- On confirmation, the creation fee is deducted from the creator's personal balance and the community is initialized.

> **Note:** If no confirmation is made within 5 minutes, the pending request expires and the region is deleted automatically. No funds are charged for an expired or cancelled request.

**Command equivalent:** `/community create <shapeType> <communityType> <name>`. Creation confirmation and cancellation are handled through chat buttons.

### After Creation

| Type | Next Step |
|---|---|
| **Manor** | Immediately enters **Pending** status. Operators are notified to audit. |
| **Realm** | Enters **Recruiting** status. Formal member count must reach **4** within **48 hours**, or the realm is revoked and the creation fee is refunded. |

---

## My Communities

Clicking **My Communities** opens a list of all communities joined by the player. Clicking any entry opens its [Community Menu](community-menu.md).

---

## Selection Mode

Selection mode is a special state in which right-clicking a block while holding a **Nether Star** registers that location as a boundary point for a new or modified region; left-clicking with the Nether Star removes the last placed point (undo). It is toggled from within the **Community Creation Screen** or **Scope Creation Screen** (reached via the Territory Menu), not from the main menu. Each time it opens for creation, chat first marks the current dimension so the new territory's anchor world is explicit.

| Action | Command |
|---|---|
| Enable | `/community select start` |
| Disable | `/community select stop` |
| Clear all points | `/community select reset` |

> **Note:** A player can enter selection mode, log off, log back in, and keep the selected points. Selection does not need to finish in a single session.

---

> **See also:** [Economy](economy.md#creation-costs) · [Community Menu](community-menu.md)
