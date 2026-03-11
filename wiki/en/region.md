# Region

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | **Region** | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

Every community plants its roots in the land. That land — the geographic foundation on which a community stands, asserts its presence, and enforces its rules — is called the community's **Region**. A Region is the complete territorial footprint of a community in the world.

But a community's territory rarely looks like a single tidy rectangle. It may be assembled from multiple distinct patches of land: a founding heartland, a later expansion, an outlying enclave, a special district. Each such individual patch is called a **Geoscope**.

**A Geoscope is the building block of a Region.** The actual territory of a Region is the union of all its Geoscopes. Everything you see highlighted on the map as belonging to a community — every chunk of land under that community's flag — is defined by the Geoscopes that community has established.

---

## How a Community's Territory Is Formed

When a community is founded, the system automatically creates its first Geoscope — the **main scope** — based on the land you selected during creation. This is the community's origin territory.

As the community grows, you can **add new Geoscopes** to expand the domain: adjacent extensions, satellite outposts, special enclaves across the map. Each Geoscope has its own independently defined shape (rectangle, circle, or polygon) to match the terrain or your building plans.

You can also **modify any Geoscope's boundaries** at any time to reshape that portion of the territory.

All Geoscopes together form the Region — the land this community actually controls in the world.

---

## Selection Mode

To define or modify a Geoscope's boundaries, you use **Selection Mode** to place boundary points directly in the world.

- Once **Selection Mode is active**, hold a **Nether Star** and **right-click blocks** to place boundary points. **Left-clicking** with the Nether Star removes the most recently placed point (one step undo).
- Three shape types are supported:
  - **Rectangle**: select two diagonal corner points; the system fills in the rectangle.
  - **Circle**: select the center, then a point on the edge to set the radius.
  - **Polygon**: select all corner points in order (must form a convex polygon).
- You can exit the menu mid-selection and continue placing points later.
- Consider marking boundary points with blocks in-world for future reference.

> **Note:** Selection Mode comes in two forms — **New Geoscope mode** for creating a Geoscope, and **Modify mode** for reshaping an existing one. You cannot have both active at once. If you open "Modify Territory" and see a warning, you are currently in New Geoscope mode. Close Selection Mode in the scope creation menu first, then retry.

---

## Adding a Geoscope

To add a new Geoscope to your community, use either entry point:

- **Territory Menu** (Main Menu → **Territory** button) → **Add Scope**; or
- Administration Menu → **Region Geometry** → **Global** → **Add Administrative District**.

Both paths open the **Scope Creation Screen**. Here you can:

1. **Enable Selection Mode** and head into the world to place boundary points.
   - The menu title guides you through each step ("→ Enable Selection Mode First" → "→ Select Points In-World").
   - Once you have enough points, a **name** button and a **confirm creation** button appear.
2. **Switch the shape** (rectangle / circle / polygon).
3. After placing points, **exit the menu** to continue in the world — or **Close Selection Mode** to cancel.
4. To start over with fresh points, click **Reset Points**.
5. Once named and ready, click **Confirm Creation** to generate a cost summary; confirm in chat to finalize.

Base cost for adding a new Geoscope:

| Community Type | Scope Addition Base Cost |
|---|---|
| Manor | $500.00 |
| Realm | $1,000.00 |

Area-based fees also apply proportionally to the Geoscope's footprint.

**Soft scope limit:** The recommended maximum number of Geoscopes is `ceil(formal_members / 2)` (formal members = owner, admin, and member roles; e.g., 1 member → limit 1, 2 members → limit 1, 3 members → limit 2). Exceeding this limit does **not** block creation; instead, a **soft-limit surcharge** is applied: the entire creation cost (fixed fee + area fee + any settings adjustments) is multiplied by **1.5^N**, where N is the number of scopes beyond the limit. For example, with a limit of 2 and 2 existing Geoscopes, adding a 3rd (N=1) multiplies the total creation cost by ×1.5; adding a 4th (N=2) multiplies it by ×2.25. This surcharge applies **only to new Geoscope creation** — it does not affect charges or refunds from modifying the geometry of existing Geoscopes (those use cumulative total area independently). When a surcharge applies, a notice appears before the confirmation dialog, and the confirmation itself shows the full formula (e.g., `rawTotal × 1.5^N = adjustedTotal`); the final deduction message also notes the surcharge amount.

> **Note:** Only **active** communities may add new Geoscopes. Communities in RECRUITING or PENDING status cannot perform this operation.

**Command equivalents:**
- `/community confirm_modification <regionId> <scopeName>` — confirm creation
- `/community cancel_modification <regionId> <scopeName>` — cancel creation

> **Quoting names:** A `<scopeName>` (or any region/community name used in commands) that contains characters other than ASCII letters and digits must be enclosed in **double quotes**: e.g. `"我的辖区"`. Tab-completion adds quotes automatically when needed.

---

## Modifying a Geoscope's Boundaries

You can redefine the boundaries of any existing Geoscope. The system calculates the cost or refund based on the area difference between old and new shapes.

**Via Territory Menu (recommended):**

1. Main Menu → **Territory** → **Modify Territory**;
2. If not already in Selection Mode, choose your community (if in multiple) and click the Geoscope you want to reshape;
3. **Modify mode** activates for that Geoscope; the menu closes and a prompt guides you to place new boundary points;
4. Hold a **Nether Star** and right-click blocks to set the new boundary (left-click to undo the last point);
5. Re-open Territory Menu → **Modify Territory** to confirm;
6. A cost summary appears in chat with a **[CONFIRM]** / **[CANCEL]** prompt (valid for 5 minutes).

**Via Administration Menu:**

1. Administration Menu → **Region Geometry** → select the Geoscope you want to reshape;
2. Modify mode activates; menu closes with a prompt;
3. Follow steps 4–6 above.

> **Visual feedback:** Once Modify mode is active, **orange dust particles** trace the full perimeter of the existing Geoscope boundary, so you can clearly see the shape you are replacing while placing new points.

**Cost rules:**
- Expanding a Geoscope: pay the area-price difference.
- Shrinking a Geoscope: receive a **50%** refund of the cost difference.

> **Note:** Only **active** communities may modify Geoscope boundaries. Communities in RECRUITING or PENDING status cannot perform this operation.

> **Note:** Requires the **Region Geometry** administration privilege.

---

## Region Settings

Each Geoscope can have its own **permissions** and **rules**, giving you granular control over what happens within that slice of territory.

Access: Administration Menu → **Region Settings** → select a Geoscope → **Settings Menu**.

### Permissions

Permissions govern what non-owner players may do within the Geoscope. Each permission can be toggled for the entire Geoscope, or assigned per-player for finer control.

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

Each permission has an area-based cost when activated. Restoring the default refunds **50%** of that cost. Per-player targeting costs **1/5** of the Geoscope-wide rate.

### Rules

Rules control automatic behaviors within the Geoscope (not tied to specific player actions).

| Rule | Description |
|---|---|
| **Spawn Monsters** | Allow hostile mob spawning |
| **Spawn Phantoms** | Allow phantom spawning |
| **TNT Block Protection** | Prevent TNT from destroying blocks |

Rules also carry area-based costs when enabled.

> **Note:** Requires the **Region Settings** administration privilege.

**Command equivalents:**
- `/community confirm_setting <regionId>` — confirm a setting change
- `/community cancel_setting <regionId>` — cancel a setting change

---

> **See also:** [Economy](economy.md#area-based-pricing) · [Administration](administration.md) · [Teleport](teleport.md)

