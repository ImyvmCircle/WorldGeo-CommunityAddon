# Region

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | **Region** | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

Every community is tied to a **region** — a defined geographic area in the Minecraft world. A region can contain one or more **scopes**, and each scope can have independent geometry, permission settings, and teleport points.

## Scopes

A **scope** is a named sub-area within a community region. The main (outermost) scope is created automatically when the community is formed. Additional scopes can be added later for more granular control.

### Adding a Scope

In the **Region Geometry** flow (Administration Menu → Region Geometry), there is an option to add a new scope. A base fee applies:

| Type | Scope Addition Base Cost |
|---|---|
| Manor | $500.00 |
| Realm | $1,000.00 |

The scope also inherits area-based pricing for any territory it occupies. After selection, a pending confirmation is generated.

**Command equivalents:**
- `/community confirm_modification <regionId> <scopeName>` — confirm a geometry change
- `/community cancel_modification <regionId> <scopeName>` — cancel a geometry change

---

## Region Geometry

The **Region Geometry** button (slot 19, Map icon) in the Administration Menu opens the **Scope Selection Menu**. Select a scope to modify its boundaries.

### Modifying Boundaries

1. Click **Region Geometry** → select the desired scope.
2. The **Global Geometry Menu** opens. Use selection mode to define new boundary points.
3. Confirm or cancel the change from the chat prompt.

**Cost:** Expanding a scope costs the area-price difference. Shrinking refunds **50%** of the cost difference.

> **Note:** Requires the **Region Geometry** admin privilege.

> **Note:** Area-based pricing uses tiered brackets. See [Economy - Area Pricing](economy.md#area-based-pricing) for the full table.

---

## Region Settings

The **Region Settings** button (slot 20, Heart of the Sea icon) opens the **Scope Selection Menu**. Select a scope to open its **Setting Menu**, where you configure permissions and rules.

### Permissions

Permissions control what non-owner players may do within the scope. Each permission can be toggled scope-wide, or assigned per-player for finer control.

| Permission | Description |
|---|---|
| **Build & Break** | Place and destroy blocks |
| **Build** | Place blocks only |
| **Break** | Destroy blocks only |
| **Container** | Open chests, barrels, shulker boxes, etc. |
| **Interaction** | Use buttons, levers, doors, etc. |
| **Redstone** | Interact with redstone components |
| **Trade** | Trade with villagers |
| **Bucket Build** | Place fluids with a bucket |
| **Bucket Scoop** | Pick up fluids with a bucket |
| **Farming** | Till soil, harvest crops |
| **PvP** | Deal damage to other players |
| **Animal Killing** | Harm passive animals |
| **Villager Killing** | Harm villagers |
| **Throwable** | Throw items (snowballs, eggs, potions, etc.) |
| **Egg Use** | Throw eggs |
| **Snowball Use** | Throw snowballs |
| **Potion Use** | Throw/use potions |
| **Ignite** | Use flint and steel or fire charges |
| **Armor Stand** | Interact with armor stands |
| **Item Frame** | Interact with item frames |

Each permission has an area-based cost when activated. Restoring the default refunds **50%** of that cost. Per-player targeting costs **1/5** of the scope-wide rate.

### Rules

Rules control automatic behaviors within the scope (not tied to specific player actions).

| Rule | Description |
|---|---|
| **Spawn Monsters** | Allow hostile mob spawning |
| **Spawn Phantoms** | Allow phantom spawning |
| **TNT Block Protection** | Prevent TNT from destroying blocks |

Rules also carry area-based costs when enabled.

> **Note:** Requires the **Region Settings** admin privilege.

**Command equivalents:**
- `/community confirm_setting <regionId>` — confirm a setting change
- `/community cancel_setting <regionId>` — cancel a setting change

---

> **See also:** [Economy](economy.md#area-based-pricing) · [Administration](administration.md) · [Teleport](teleport.md)
