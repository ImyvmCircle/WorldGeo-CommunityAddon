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
| 13 | Diamond Pickaxe | **Create Community** | Opens the Community Creation Menu |
| 16 | Red Bed | **My Communities** | Opens your personal community list |
| 19 | Anvil | **OP Functions** | *(Operators only)* Server management tools |
| 22 | Command Block / Redstone Block | **Selection Mode** | Toggle region-point selection mode on/off |
| 31 | Brush | **Reset Selection** | Clear all selected points *(visible only when selection mode is on)* |
| 44 | Lime/Gray Dye | **Action Bar** | Toggle the region-location action bar display |

---

## Browse Communities

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

Clicking **Create Community** opens the **Community Creation Menu**.

### Step 1 — Select a Region

Before creating, you must select a geographic area. Use **Selection Mode** to do this:

1. Click **Selection Mode: Disabled** in the Main Menu (slot 22) to enable it, or run `/community select start`.
2. With a **command block** in your hand, right-click blocks to set boundary points.
3. Run `/community select stop` or click the button again to exit selection mode.
4. To start over, click **Reset Selection** (slot 31) or run `/community select reset`.

> **Tip:** Mark boundary corners with blocks before selecting to make future modifications easier.

#### Shape Types

| Shape | Points Needed | How Points Are Interpreted |
|---|---|---|
| **Rectangle** | 2 | Two diagonal corner points |
| **Circle** | 2 | Center point + one point on the circumference |
| **Polygon** | 3+ | All vertices in order (must be convex) |

### Step 2 — Configure in the Creation Menu

The **Community Creation Menu** shows:

| Slot | Icon | Button | Action |
|---|---|---|---|
| 10 | Name Tag | **Community Name** | Click to open an anvil and type the name |
| 13 | Clock / Map / Nether Star | **Shape** | Click to cycle through Rectangle → Circle → Polygon |
| 16 | Birch / Cherry Planks | **Type** | Click to toggle between Manor and Realm |
| 35 | Emerald Block | **Confirm Creation** | Submit the creation request |

The menu title displays the current community name and any validation errors (e.g., name already taken, insufficient funds).

### Step 3 — Confirm and Pay

After clicking **Confirm Creation**:

- The region is created immediately.
- A cost summary is shown in chat, including base cost and area surcharge.
- An interactive **[CONFIRM]** / **[CANCEL]** prompt appears in chat. You have **5 minutes** to confirm.
- On confirmation, the creation fee is deducted and the community is initialized.

> **Note:** If you do not confirm within 5 minutes, the pending request expires and the region is deleted automatically. No funds are charged for an expired or cancelled request.

**Command equivalent:** `/community create <shapeType> <communityType> <name>`  
Confirmation: `/community confirm_creation <regionId>` | `/community cancel_creation <regionId>`

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

Selection mode is a special state in which right-clicking a block with a **command block** in hand registers that location as a boundary point for a new or modified region.

| Action | Menu Button | Command |
|---|---|---|
| Enable | Click **Selection Mode: Disabled** (slot 22) | `/community select start` |
| Disable | Click **Selection Mode: Enabled** (slot 22) | `/community select stop` |
| Clear all points | Click **Reset Selection** (slot 31) | `/community select reset` |

> **Note:** You can enter selection mode, log off, log back in, and your points are retained. You do not need to complete selection in a single session.

---

> **See also:** [Economy](economy.md#creation-costs) · [Community Menu](community-menu.md)
