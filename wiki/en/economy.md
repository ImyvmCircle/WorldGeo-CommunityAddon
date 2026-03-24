# Economy

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | **Economy** | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

Communities involve several types of in-game currency transactions. This page covers all costs, the community treasury, and the donation system.

## Cost Overview

| Transaction | Manor | Realm |
|---|---|---|
| Community creation (base) | $5,000.00 | $10,000.00 |
| Area surcharge | Tiered (see below) | Tiered (see below) |
| Join fee | $450.00 | $500.00 |
| Scope addition (base) | $500.00 | $1,000.00 |
| 1st teleport point | Free | Free |
| 2nd teleport point (base) | $2,000.00 | $2,000.00 |
| Additional teleport point | Doubles per additional | Doubles per additional |
| Modify existing teleport point | $1,000.00 | $1,000.00 |

## Creation Costs

When creating a community, you pay a **base creation fee** plus an **area surcharge** for territory above the free threshold.

### Area-Based Pricing

The area surcharge uses a **tiered bracket system** (progressive, like a tax bracket). Only the area within each bracket is charged at that bracket's rate. The unit multiplier doubles with each tier.

#### Manor (first 10,000 m² free; base rate: $200 per 10,000 m²)

| Tier | Area Range | Multiplier | Effective Rate |
|---|---|---|---|
| 1 | 10,000 – 40,000 m² | ×1 | $0.020 / m² |
| 2 | 40,000 – 160,000 m² | ×2 | $0.040 / m² |
| 3 | 160,000 – 640,000 m² | ×4 | $0.080 / m² |
| 4 | 640,000 – 2,560,000 m² | ×8 | $0.160 / m² |
| … | … | doubles | … |

#### Realm (first 40,000 m² free; base rate: $600 per 40,000 m²)

| Tier | Area Range | Multiplier | Effective Rate |
|---|---|---|---|
| 1 | 40,000 – 160,000 m² | ×1 | $0.015 / m² |
| 2 | 160,000 – 640,000 m² | ×2 | $0.030 / m² |
| 3 | 640,000 – 2,560,000 m² | ×4 | $0.060 / m² |
| … | … | doubles | … |

> **Tip:** Realms have a lower per-m² rate than manors at every tier, making large territory significantly cheaper for realms.

### Refund Policy

When **reducing** territory area, you receive a **50% refund** of the cost difference. When **selling** a scope to the system, you receive a **50% refund** of the corresponding land cost and setting cost difference (base cost is not refunded).

---

## Join Fees

Joining a community costs a one-time fee, paid by the joining player:

| Community Type | Join Fee |
|---|---|
| Manor | $450.00 |
| Realm | $500.00 |

- **Open policy:** Fee deducted immediately on joining.
- **Application policy:** Fee deducted when submitting the application. If refused, the fee is **fully refunded**.
- **Invite-Only:** When an invited player's membership is approved by an admin, the join fee is deducted from the **community treasury** (not the player).

---

## Treasury Menu

Clicking **Treasury** (slot 21, Gold Ingot) in the [Community Menu](community-menu.md) opens the **Community Treasury Menu**, which displays:

- **Total Assets** — sum of all member donations and all received treasury grants minus all recorded expenditures.
- **Donor List button** — opens a ranked list of contributors.
- **Donate button** — opens the [Donation Menu](#donating).

### Donor List

The **Donor List Menu** shows all members who have donated, ranked by total contribution (descending). Clicking a donor opens their **Donor Details Menu**, listing every individual donation with timestamps.

---

## Donating

Any formal member (Owner, Admin, Member) can donate to the community treasury.

### How to Donate

1. Open the [Community Menu](community-menu.md).
2. Click **Donate** (slot 30, Emerald) — or click **Donate** inside the Treasury Menu.
3. In the **Donation Menu**, select or enter the amount.
4. Confirm to transfer funds from your personal balance to the treasury.

The donation is recorded in your member account and contributes to the treasury total.

---

## Treasury Grants

The community Owner or any Admin with the **Grant Coins from Treasury** privilege can send coins from this community's treasury to another community's treasury.

### Initiating a Grant

1. Open the **Administration Menu** and click **Treasury Grant** (slot 13, Gold Ingot);
2. Select the recipient community from the target list;
3. Choose the amount in the **Grant Amount Menu**;
4. The system posts a chat confirmation request with clickable buttons.

### Confirmation Flow

| Action | Performed by | Effect |
|---|---|---|
| **[Accept]** | Owner or eligible admin of the **target** community | Executes the transfer; funds leave the source treasury and enter the target treasury |
| **[Decline]** | Owner or eligible admin of the **target** community | Rejects the request |
| **[Cancel]** | Owner or eligible admin of the **source** community | Withdraws the request |

The confirmation prompt expires after **5 minutes**.

### Accounting

- The grant amount is recorded as an **expenditure** in the source community's treasury.
- The grant amount is recorded as an **incoming grant** in the target community's treasury.
- Both communities' **Total Assets** are updated automatically.

> **Note:** Both initiating and accepting parties require the **Grant Coins from Treasury** privilege. Owners are always exempt.

---

## Expenditures

Community expenditures are recorded automatically whenever the treasury pays for:

- Approving invited members (join fee paid from treasury).
- Teleport point creation beyond the first free point.
- Teleport point modification.

All expenditures are subtracted from the **Total Assets** calculation in the Treasury Menu.

---

> **See also:** [Region](region.md) · [Teleport](teleport.md) · [Main Menu](main-menu.md#create-community)
