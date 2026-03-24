# Main Menu

> **Navigation:** [Index](index.md) | **Main Menu** | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

The **Community Main Menu** is the entry point for all community features. Open it at any time by running `/community` with no arguments.

## Opening the Menu

| Method | How |
|---|---|
| Command | `/community` |

The menu is a chest-style GUI containing the following buttons:

| Slot | Icon | Button | Description |
|---|---|---|---|
| 10 | Writable Book | **Browse Communities** | Opens the Community List Menu |
| 13 | Diamond Pickaxe | **圈地 (Territory)** | Opens the Territory Menu for land-claiming operations |
| 16 | Red Bed | **My Communities** | Opens your personal community list |
| 19 | Anvil | **OP Functions** | *(Operators only)* Server management tools |
| 44 | Lime/Gray Dye | **Action Bar** | Toggle the region-location action bar display |

---

## 圈地 (Territory)

Clicking **圈地** opens the **Territory Menu** — the hub for all land-claiming operations.

| Slot | Icon | Button | Description |
|---|---|---|---|
| 10 | Diamond Pickaxe | **Create Community (创建聚落)** | Opens the Community Creation screen |
| 13 | Grass Block | **Add Scope (增加辖区)** | Add a new scope to an existing community |
| 16 | Shears | **Modify Territory (修改地块)** | Modify the geometry of an existing scope |

### Create Community (创建聚落)

Opens the **Community Creation Screen**, which integrates point selection directly:

| Slot | Icon | Button | Action |
|---|---|---|---|
| 10 | Redstone/Emerald | **Selection Mode** | Toggle selection mode on/off |
| 12 | Clock/Map/Nether Star | **Shape** | *(visible in selection mode)* Cycle through Rectangle → Polygon → Circle |
| 14 | Barrier | **Exit to Select** | *(visible in selection mode)* Close screen to place points in the world |
| 28 | Name Tag | **Community Name** | *(visible when ≥2 points)* Open anvil to name the community |
| 34 | Birch/Cherry Planks | **Community Type** | *(visible when ≥2 points)* Toggle Manor / Realm |
| 35 | Emerald Block | **Confirm Creation** | *(visible when ≥2 points)* Submit the creation request |

**How to use:**

1. Click **Create Community** in the Territory Menu.
2. In the creation screen, click **Selection Mode** to enable it. The screen closes.
3. Right-click blocks in the world while holding a **Nether Star** to set boundary points. Left-click the Nether Star to remove the last placed point (undo).
4. Re-open the Territory Menu → **Create Community** to return to the screen with your points loaded.
5. Optionally switch shape, set a name, choose type, then click **Confirm Creation**.

> **Tip:** Use `/community select start` and `/community select stop` as command alternatives for toggling selection mode.

### Add Scope (增加辖区)

Opens the scope creation flow:

1. If in multiple communities, select which one to add a scope to.
2. Permission is checked (requires **Region Geometry** privilege).
3. The **Scope Creation Screen** opens — same layout as the Community Creation Screen.

> **Warning:** If you are currently in **ModifyExisting** selection mode (i.e. modifying an existing scope), you must complete or cancel that operation before adding a scope.

### Modify Territory (修改地块)

Opens the scope modification flow:

1. If already in **ModifyExisting** selection mode (i.e. you already chose a scope to modify and have selected new points), the modification executes immediately.
2. If not in selection mode, select a community (if in multiple), then select a scope from the list.
3. Clicking a scope starts **ModifyExisting** selection mode and closes the menu with a prompt.
4. Select new boundary points in the world, then re-open the Territory Menu → **Modify Territory** to confirm.

> **Warning:** If you are in **Normal** (creation) selection mode, the menu closes with a warning to exit that mode first.

> **Note:** Requires the **Region Geometry** admin privilege.

Clicking **Browse Communities** opens the **Community List Menu**, which displays all communities on the server as clickable items. Use the filter buttons at the top to narrow the list:

| Filter | Description |
|---|---|
| **All** | Every community regardless of status |
| **Joinable** | Communities you can currently join |
| **Recruiting** | Realms actively seeking founding members |
| **Auditing** | Communities pending operator approval |
| **Active** | Fully operational communities |
| **Revoked** | Deactivated communities |

Each entry shows the community name, ID, founding time, status, join policy, and member count. Clicking an entry opens the [Community Menu](community-menu.md) (if you are a member) or the non-member join view.

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

- The region is created immediately.
- A cost summary is shown in chat, including base cost and area surcharge.
- An interactive **[CONFIRM]** / **[CANCEL]** prompt appears in chat. You have **5 minutes** to confirm.
- On confirmation, the creation fee is deducted and the community is initialized.

> **Note:** If you do not confirm within 5 minutes, the pending request expires and the region is deleted automatically. No funds are charged for an expired or cancelled request.

**Command equivalent:** `/community create <shapeType> <communityType> <name>`  
Confirmation: `/commun confirm_creation <regionId>` | `/commun cancel_creation <regionId>`

### After Creation

| Type | Next Step |
|---|---|
| **Manor** | Immediately enters **Pending** status. Operators are notified to audit. |
| **Realm** | Enters **Recruiting** status. You must attract **at least 4 members within 48 hours**, or the realm is revoked and the creation fee is refunded. |

---

## My Communities

Clicking **My Communities** opens a list of all communities you belong to. Click any entry to open its [Community Menu](community-menu.md).

---

## Selection Mode

Selection mode is a special state in which right-clicking a block while holding a **Nether Star** registers that location as a boundary point for a new or modified region; left-clicking with the Nether Star removes the last placed point (undo). It is toggled from within the **Community Creation Screen** or **Scope Creation Screen** (reached via the Territory Menu), not from the main menu.

| Action | Command |
|---|---|
| Enable | `/community select start` |
| Disable | `/community select stop` |
| Clear all points | `/community select reset` |

> **Note:** You can enter selection mode, log off, log back in, and your points are retained. You do not need to complete selection in a single session.

---

> **See also:** [Economy](economy.md#creation-costs) · [Community Menu](community-menu.md)
