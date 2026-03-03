# Administration

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | **Administration** | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

The **Community Administration Menu** is accessible from the **Administration** button (slot 12, Anvil icon) in the [Community Menu](community-menu.md). It is visible only to the **Owner** and **Administrators**.

## Administration Menu Layout

| Slot | Icon | Button | Description |
|---|---|---|---|
| 10 | Name Tag | **Rename** | Change the community's name via an anvil interface |
| 11 | Player Head | **Manage Members** | View and manage all formal members |
| 12 | Redstone Torch | **Audit Applications** | Review pending join applications |
| 13 | Paper | **Announcements** | Create and manage community announcements |
| 14 | Item Frame | **Advancement** | *(Planned feature)* |
| 19 | Map | **Region Geometry** | Modify the territory's geographic shape |
| 20 | Heart of the Sea | **Region Settings** | Adjust territory permissions and rules |
| 21 | Ender Pearl | **Teleport Points** | Set, configure, and manage teleport destinations |
| 28 | Green/Yellow/Red Wool | **Join Policy** | Cycle through Open → Application → Invite-Only |

---

## Rename

Clicking **Rename** opens an anvil interface. Type the new name and take the output item to confirm.

> **Note:** This action requires the **Rename Community** privilege to be enabled for your Admin role (Owners are always exempt from privilege checks).

**Command equivalent:** N/A (rename is menu-only)

---

## Manage Members

Clicking **Manage Members** opens the **Member List Menu** in admin view, showing all members with their roles.

### Member Detail Menu

Clicking a member's head opens their **Member Detail Menu**, where an admin can:

- **Promote to Admin / Demote to Member** — changes the member's role.
- **Remove from Community** — kicks the member.
- **Manage Admin Privileges** — opens the **Admin Privilege Menu** for that admin (Owner only).

### Restrictions

| Action | Owner | Admin |
|---|---|---|
| Promote / Demote Members | ✔ | ✔ (Members only) |
| Remove Members | ✔ | ✔ (Members only) |
| Promote to Admin | ✔ | ✗ |
| Demote / Remove other Admins | ✔ | ✗ |
| Demote / Remove Owner | ✗ | ✗ |

### Admin Privileges

The **Owner** can individually toggle each privilege for any Admin. Privileges are toggled in the **Admin Privilege Menu** (opened from the Member Detail Menu).

| Privilege | Description |
|---|---|
| **Rename Community** | Allow admin to rename the community |
| **Manage Members** | Allow admin to promote/demote/remove members |
| **Audit Applications** | Allow admin to accept or refuse applicants |
| **Manage Announcements** | Allow admin to create and delete announcements |
| **Manage Advancement** | Allow admin to manage the advancement tree *(planned)* |
| **Region Geometry** | Allow admin to modify territory shape and size |
| **Region Settings** | Allow admin to adjust territory permissions and rules |
| **Teleport Points** | Allow admin to set and configure teleport points |
| **Change Join Policy** | Allow admin to change the community join policy |

All privileges are **enabled by default** for Admins. The Owner can disable any subset.

---

## Audit Applications

Clicking **Audit Applications** opens the **Applicant List Menu**, listing all players in **Applicant** status.

- Clicking an applicant opens their **Audit Menu** with **Accept** and **Refuse** buttons.

### Accept

- The applicant is promoted to **Member**.
- They receive a mail notification.
- If the applicant was **invited**, the join fee is deducted from the community treasury. If the community cannot afford it, the audit is blocked.
- If the applicant applied directly (**Application** policy), their fee was already paid at application time.

### Refuse

- For a **direct applicant**: the join fee is **refunded** to the applicant's account, and their status is set to **Refused**.
- For an **invited applicant**: the invitation record is removed; no fee is involved (fee was not charged at invite time).

> **Note:** Requires the **Audit Applications** privilege.

---

## Announcements

Clicking **Announcements** opens the **Announcement List Menu** (admin view), which shows all announcements including deleted ones (marked separately).

### Creating an Announcement

Click **Create Announcement** in the list menu. An anvil interface appears — type the announcement content.

- The announcement is published immediately to all members.
- Members see a notification the next time they log in or open the announcement list.
- Read status is tracked per member.

**Command equivalent:** `/community announcement create <communityIdentifier> <content>`

### Deleting an Announcement

Click an announcement in the list to open its detail view, then click **Delete**. The announcement is soft-deleted (hidden from members but retained in records).

> **Note:** Requires the **Manage Announcements** privilege.

**Command equivalent:** `/community announcement delete <communityIdentifier> <announcementId>`

---

## Join Policy

The **Join Policy** button (slot 28) cycles through the three policies each time it is clicked:

| Policy | Icon | Behaviour |
|---|---|---|
| **Open** | Green Wool | Players pay the join fee and become Members immediately |
| **Application** | Yellow Wool | Players pay the fee, become Applicants, and await admin approval |
| **Invite-Only** | Red Wool | Players can only join via an invitation from a member |

> **Note:** Requires the **Change Join Policy** privilege.

---

## Region Geometry, Region Settings, Teleport Points

These three buttons open the **Scope Selection Menu**, where you choose which geographic scope to operate on. See the dedicated pages:

- [Region](region.md) — Geometry and Settings
- [Teleport](teleport.md) — Teleport Points

---

> **See also:** [Region](region.md) · [Teleport](teleport.md) · [Economy](economy.md)
