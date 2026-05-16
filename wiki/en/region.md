# Region

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | **Region** | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

Every community plants its roots in the land. That land — the geographic foundation on which a community stands, asserts its presence, and enforces its rules — is the community's **region**. A region is the complete territorial footprint of a community in the world.

But a community's territory rarely looks like a single tidy rectangle. It may be assembled from multiple distinct patches of land: a founding heartland, a later expansion, an outlying enclave, or a special district. Each individual patch is called a **scope**.

**Scopes are the building blocks of a region.** The actual territory of a region is the union of all its scopes. Land highlighted as belonging to a community is defined by the scopes that community has established.

Area in the Overworld, Nether, and End is recorded separately, forming a cross-dimensional territorial ledger.

---

## How a Community's Territory Is Formed

When a community is founded, the system automatically creates its first scope — the **main scope** — based on the selected land. This is the community's origin territory.

As the community grows, new scopes can expand the domain: adjacent extensions, satellite outposts, or special enclaves across the map. Each scope has its own independently defined shape: rectangle, circle, or polygon.

Existing scope boundaries can also be modified after creation.

All scopes together form the region — the land this community controls in the world.

---

## Selection Mode

To define or modify a scope's boundaries, **Selection Mode** places boundary points directly in the world.

- Once **Selection Mode is active**, hold a **Nether Star** and **right-click blocks** to place boundary points. **Left-clicking** with the Nether Star removes the most recently placed point (one step undo).
- Three shape types are supported:
  - **Rectangle**: select two diagonal corner points; the system fills in the rectangle.
  - **Circle**: select the center, then a point on the edge to set the radius.
  - **Polygon**: select all corner points in order (must form a convex polygon).
- The menu can be closed mid-selection, and point placement can continue later.

> **Note:** Selection Mode comes in two forms: new scope mode for creating a scope, and Modify mode for reshaping an existing one. The two modes cannot be active at the same time. If "Modify Territory" opens a warning, new scope mode is currently active and must be closed from the scope creation menu first.

---

## Adding a Scope

A new scope can be added from either entry point:

- **Territory Menu** (Main Menu → **Territory** button) → **Add Scope**; or
- Administration Menu -> **Region Geometry** -> **Global** -> **Add Administrative District**.

Both paths open the **Scope Creation Screen**. It supports the following flow:

1. **Enable Selection Mode** and place boundary points in the world.
   - The menu title shows each step ("-> Enable Selection Mode First" -> "-> Select Points In-World").
   - Chat first marks the current creation dimension: green for the Overworld, red for the Nether, pale gold-white for the End.
   - Once enough points are selected, a **name** button and a **confirm creation** button appear.
2. **Switch the shape** (rectangle / circle / polygon).
3. After placing points, **exit the menu** to continue in the world — or **Close Selection Mode** to cancel.
4. To start over with fresh points, click **Reset Points**.
5. Once named and ready, click **Confirm Creation** to generate a cost summary; confirm in chat to finalize.

The creation confirmation repeats the destination dimension, so the new territory is tied to its world in plain sight from the first quote onward.

Base cost for adding a new scope:

| Community Type | Scope Addition Base Cost |
|---|---|
| Manor | $500.00 |
| Realm | $1,000.00 |

Area-based fees also apply proportionally to the scope's footprint.

If the new scope stands in the Nether, fees tied directly to that scope are settled at x8. If it stands in the End, they are settled at x2. The Overworld remains x1. Region-wide land and setting costs are calculated per dimension and then summed.

**Soft scope limit:** The recommended maximum number of scopes is `ceil(formal_members / 2)` (formal members = owner, admin, and member roles; e.g., 1 member -> limit 1, 2 members -> limit 1, 3 members -> limit 2). Exceeding this limit does **not** block creation; instead, a **soft-limit surcharge** applies. The entire creation cost (fixed fee + area fee + any settings adjustments) is multiplied by **1.5^N**, where N is the number of scopes beyond the limit. For example, with a limit of 2 and 2 existing scopes, adding a 3rd (N=1) multiplies the total creation cost by x1.5; adding a 4th (N=2) multiplies it by x2.25. This surcharge applies **only to new scope creation**. It does not affect charges or refunds from modifying existing scopes. When a surcharge applies, chat shows the formula before final confirmation and notes the surcharge amount after deduction.

> **Note:** Only **active** communities may add new scopes. Communities in RECRUITING or PENDING status cannot perform this operation.

Scope creation confirmation and cancellation are handled through chat buttons.

> **Quoting names:** A `<scopeName>` (or any region/community name used in commands) that contains characters other than ASCII letters and digits must be enclosed in **double quotes**: e.g. `"我的辖区"`. Tab-completion adds quotes automatically when needed.

---

## Selling a Scope

A scope can be sold back to the system. The refund is calculated from the scope's area and affected settings, returning **50%** of the corresponding cost difference. The base creation cost is not refunded. Each community must retain at least one scope, so the last remaining scope cannot be sold.

**Steps:**

1. Administration Menu -> **Geographic Scope Modification** -> **Global** -> **Sell Administrative District**;
2. Select the scope to sell from the list;
3. A refund summary with **[CONFIRM]** / **[CANCEL]** buttons appears in chat (valid for 5 minutes).

> **Note:** Only **active** communities can sell scopes.

> **Note:** Requires **Geographic Scope Modification** administrative privilege.

Scope sale confirmation and cancellation are handled through chat buttons.

---

## Modifying Scope Boundaries

The boundaries of any existing scope can be redefined. The cost or refund is calculated from the area difference between the old and new shapes.

**Via Territory Menu (recommended):**

1. Main Menu -> **Territory** -> **Modify Territory**;
2. If Selection Mode is not already active, choose the community when needed and click the scope to reshape;
3. **Modify mode** activates for that scope; the menu closes and a prompt guides boundary point placement;
4. Hold a **Nether Star** and right-click blocks to set the new boundary (left-click to undo the last point);
5. Re-open Territory Menu -> **Modify Territory** to confirm;
6. A cost summary appears in chat with a **[CONFIRM]** / **[CANCEL]** prompt (valid for 5 minutes).

**Via Administration Menu:**

1. Administration Menu -> **Region Geometry** -> select the scope to reshape;
2. Modify mode activates; menu closes with a prompt;
3. Follow steps 4–6 above.

No matter which entry path is used, both starting Modify mode and entering modification confirmation require standing in the **same dimension as the target scope**. If the selection is made from another dimension, the menu closes, chat states both the scope's dimension and the current one, and a clickable **return** button is provided.

> **Visual feedback:** Once Modify mode is active, **orange dust particles** trace the full perimeter of the existing scope boundary, making the replaced shape visible while new points are placed.

**Cost rules:**
- Expanding a scope: pay the area-price difference.
- Shrinking a scope: receive a **50%** refund of the cost difference.

The confirmation screen lists area in each dimension before and after the change, and marks the dimension that actually changed together with the exact delta.

> **Note:** Only **active** communities may modify scope boundaries. Communities in RECRUITING or PENDING status cannot perform this operation.

> **Note:** Requires the **Region Geometry** administration privilege.

---

## Region Settings

Each scope can have its own **permissions** and **rules**, giving the community fine-grained control over what happens within that slice of territory.

Access: Administration Menu -> **Region Settings** -> select a scope -> **Settings Menu**.

### Permissions

Permissions govern what non-owner players may do within the scope. Each permission can be toggled for the entire scope, or assigned per-player for finer control.

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
| **Wind Charge Throwing** | Throw wind charges |

Each permission has an area-based cost when activated. Restoring the default refunds **50%** of that cost. Per-player targeting costs **1/5** of the scope-wide rate.

Permissions attached to the full region are priced by dimension and then combined. Permissions attached to a single scope inherit that scope's dimension multiplier.

### Rules

Rules control automatic behaviors within the scope, not tied to specific player actions.

| Rule | Description |
|---|---|
| **Spawn Monsters** | Allow hostile mob spawning |
| **Spawn Phantoms** | Allow phantom spawning |
| **TNT Block Protection** | Prevent TNT from destroying blocks |
| **Enderman Block Pickup** | Allow Endermen to pick up blocks within the scope |
| **Sculk Spreading** | Allow sculk sensors to spread within the scope |
| **Snow Golem Trail** | Allow Snow Golems to leave a snow trail |
| **Dispenser Firing** | Allow dispensers to fire within the scope |
| **Pressure Plate Triggering** | Allow pressure plates to be triggered within the scope |
| **Piston Movement** | Allow pistons to push blocks within the scope |

Rules also carry area-based costs when enabled.

Setting confirmations list the current area in each dimension.

### Setting Costs

Setting costs scale progressively with territory size (bracketed by the community's free-area threshold: 10,000 m² for manors, 40,000 m² for realms). Small communities typically stay in the first tier; the per-unit rate rises as territory grows. The table below shows the **base rate** (first tier, per 10,000 m²) for each setting:

| Setting | Base Rate |
|---|---|
| Build & Break | $200.00 |
| Interaction | $150.00 |
| Build | $125.00 |
| Break | $125.00 |
| PvP | $125.00 |
| Container | $100.00 |
| Redstone | $100.00 |
| Villager Killing | $100.00 |
| Animal Killing | $70.00 |
| Spawn Monsters (rule) | $450.00 |
| Spawn Phantoms (rule) | $70.00 |
| TNT Block Protection (rule) | $70.00 |
| Enderman Block Pickup (rule) | $70.00 |
| Sculk Spreading (rule) | $70.00 |
| Snow Golem Trail (rule) | $70.00 |
| Dispenser Firing (rule) | $70.00 |
| Pressure Plate Triggering (rule) | $70.00 |
| Piston Movement (rule) | $70.00 |
| Farming | $35.00 |
| Armor Stand | $35.00 |
| Item Frame | $35.00 |
| Trade | $30.00 |
| Bucket Build | $20.00 |
| Bucket Scoop | $20.00 |
| Throwable | $20.00 |
| Ignite | $20.00 |
| Potion Use | $10.00 |
| Wind Charge Throwing | $8.00 |
| Egg Use | $5.00 |
| Snowball Use | $5.00 |

- **Per-player targeting** costs **1/5** of the scope-wide rate.
- **Restoring to default** refunds **50%** of the original cost.

> **Note:** Requires the **Region Settings** administration privilege.

Setting change confirmation and cancellation are handled through chat buttons.

---

> **See also:** [Economy](economy.md#area-based-pricing) · [Administration](administration.md) · [Teleport](teleport.md)
