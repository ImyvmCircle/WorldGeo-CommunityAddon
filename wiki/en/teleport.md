# Teleport

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | **Teleport** | [Chat](chat.md) | [Commands](commands.md)

---

Communities can set **teleport points** within each scope. Members and visitors can use these points to travel directly into the community territory.

## Using Teleport

Two buttons in the [Community Menu](community-menu.md) initiate teleportation:

| Button | Slot | Behaviour |
|---|---|---|
| **Teleport to Community** | 24 (Ender Pearl) | Teleports immediately to the main scope's teleport point |
| **Scope Teleport** | 25 (Compass) | Opens the Scope Selection Menu to choose a destination |

### Access Rules

| Visitor Type | Access |
|---|---|
| Owner, Admin, Member | Always allowed (as long as a teleport point is set) |
| Non-member (Applicant, public) | Only if the scope's teleport point is set to **Public** |

---

## Daily Usage and Costs

Each player has a per-community daily teleport quota. After the free quota is exhausted, teleports incur a fee and a delay. The quota resets daily.

| Role | Free Uses per Day |
|---|---|
| Owner / Admin / Member (formal) | 10 |
| Non-formal (Applicant, public) | 1 |

### Paid Teleport Tiers

Once the free quota is exhausted, each additional use costs more and adds a delay:

| Paid Use # | Fee | Delay |
|---|---|---|
| 1st paid | $10.00 | 2 seconds |
| 2nd paid | $20.00 | 4 seconds |
| 3rd paid | $40.00 | 8 seconds |
| nth paid | $10.00 × 2^(n-1) | 2 × 2^(n-1) seconds |

> **Tip:** Moving or taking damage during the delay **cancels** the teleport (no fee charged). Insufficient balance at the moment of execution also cancels the teleport.

> **Dimension multiplier**  
> Paid teleports aimed at a specific Geoscope read the destination dimension. Overworld ×1. Nether ×8. End ×2.

---

## Managing Teleport Points (Admins)

Teleport points are set per-scope from **Administration Menu → Teleport Points** (slot 21, Ender Pearl). Clicking this button opens the **Scope Selection Menu** — select a scope to open its **Teleport Point Menu**.

### Teleport Point Menu

| Action | Description |
|---|---|
| **Inquire** | Displays the current teleport point coordinates in chat |
| **Set Teleport Point** | Sets your current standing position as the teleport point (with cost confirmation) |
| **Toggle Public/Private** | Switches the point between Public and Private access |
| **Reset** | Removes the teleport point from the scope |

### Setting a Teleport Point

1. Stand at the desired destination.
2. Open **Administration → Teleport Points → [scope]**.
3. Click **Set Teleport Point**.
4. A cost summary is shown in chat. An interactive **[CONFIRM]** / **[CANCEL]** prompt appears. You have **5 minutes** to confirm.
5. On confirmation, the cost is deducted from the **community treasury** and the point is saved.

### Costs

| Situation | Cost |
|---|---|
| First teleport point in a scope | Free |
| 2nd active point (base) | $2,000.00 |
| 3rd active point | $4,000.00 |
| nth active point | $2,000.00 × 2^(n-2) |
| Modifying an existing point | $3,000.00 |

> **Note:** The teleport point costs are deducted from the **community treasury**, not the admin's personal balance. Ensure the treasury has sufficient funds before setting points.
>
> **Dimension multiplier**  
> If the hosting scope is in the Nether, the above prices are settled at ×8. If it is in the End, they are settled at ×2. The Overworld remains ×1.

> **Note:** Requires the **Manage Teleport Points** admin privilege.

**Command equivalents:**
- `/commun confirm_teleport_point_set <regionId> <scopeName>` — confirm setting a teleport point

---

## Public vs. Private

Each teleport point can be toggled between **Public** and **Private**:

| Setting | Who Can Use |
|---|---|
| **Public** | All players (including non-members) |
| **Private** | Formal members only (Owner, Admin, Member) |

Toggle using the **Toggle Public/Private** button in the Teleport Point Menu.

---

> **See also:** [Economy](economy.md) · [Region](region.md) · [Administration](administration.md)
