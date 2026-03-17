# Community Menu

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | **Community Menu** | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

The **Community Menu** is the central hub for any community member. It is opened by clicking a community in the **My Communities** list or the **Community List Menu**.

## Layout

| Slot | Icon | Button | Who Can See | Description |
|---|---|---|---|---|
| 10 | Player Head | **Community Banner** | All members | Displays community name and ID; non-interactive |
| 12 | Anvil | **Administration** | Owner, Admin | Opens the [Administration Menu](administration.md) |
| 19 | Bookshelf | **Region Info** | All members | Sends the region description to chat and closes the menu |
| 20 | Map | **Announcements** | All members | Opens the [Announcement List](#announcements) |
| 21 | Gold Ingot | **Treasury** | All members | Opens the [Community Treasury](economy.md#treasury-menu) |
| 22 | Armor Stand | **Members** | All members | Opens the [Member List](#member-list) |
| 23 | Heart of the Sea | **Settings** | All members | Opens the [Community Settings](#community-settings) |
| 24 | Ender Pearl | **Teleport to Community** | All members | Teleports directly to the community's main scope point |
| 25 | Compass | **Scope Teleport** | All members | Opens the scope selection menu for targeted teleport |
| 28 | Writable Book | **Chat Channel** | All members | Opens the [Chat Room Menu](chat.md) |
| 29 | Experience Bottle | **Advancement** | All members | *(Planned feature)* |
| 30 | Emerald | **Donate** | All members | Opens the [Donation Menu](economy.md#donating) |
| 32 | Pink Dye | **Like** | All members | Like the community; each player can like once per day |
| 33 | Zombie Villager Spawn Egg | **Leave Community** | Member, Admin | Opens a confirmation screen to leave |
| 34 | Villager Spawn Egg | **Invite Member** | All formal members | Opens the online player list to send an invitation |

---

## Region Info

Clicking **Region Info** sends the community's region details (boundaries, ownership, etc.) directly to your chat log. The menu closes automatically.

**Command equivalent:** `/community query <communityIdentifier>`

---

## Announcements

Clicking **Announcements** opens the **Announcement List Menu**, which shows all active announcements for the community. Unread announcements are highlighted.

- Click an announcement to open its **Detail View** with the full content.
- Reading an announcement marks it as read for your account.

**Command equivalents:**
- `/community announcement list <communityIdentifier>` — list all announcements
- `/community announcement view <communityIdentifier> <announcementId>` — view a specific announcement

Administrators can create and delete announcements from the [Administration Menu](administration.md#announcements).

---

## Member List

Clicking **Members** opens the **Member List Menu**, showing all formal members (Owner, Admin, Member). Each entry is the member's player head.

- Clicking a member opens their **Member Detail Menu**, showing their role and join date.
- Administrators can manage members from this menu (see [Administration → Manage Members](administration.md#manage-members)).

---

## Community Settings

Clicking **Settings** opens the **Community Settings Menu** where members can view the current **Join Policy**. The owner and administrators (with the appropriate privilege) can toggle the join policy here.

| Join Policy | Icon | Description |
|---|---|---|
| **Open** | Green Wool | Anyone can join directly by paying the join fee |
| **Application** | Yellow Wool | Applicants pay the fee and wait for admin approval |
| **Invite-Only** | Red Wool | Only invited players can join |

> **Note:** Changing the join policy may require the **Change Join Policy** admin privilege to be enabled for your role.

---

## Teleport

Two teleport buttons are available in the Community Menu:

| Button | Behaviour |
|---|---|
| **Teleport to Community** (slot 24) | Immediately teleports to the community's main scope teleport point |
| **Scope Teleport** (slot 25) | Opens a scope selection list; teleport to the chosen scope's teleport point |

Both options are subject to daily usage limits and fees. See [Teleport](teleport.md) for full details.

**Command equivalent:** N/A (teleport is menu-only)

---

## Invite Member

Clicking **Invite Member** opens a list of currently **online players**. Click a player's head to send them an invitation.

- The invited player receives a chat message with clickable **[Accept]** / **[Reject]** buttons.
- They have **5 minutes** to respond.
- On acceptance, the applicant enters **Applicant** status and awaits admin approval.
- If the community treasury does not have enough to cover the join fee, the invitation cannot be sent.

**Invite flow:**
1. Member clicks **Invite Member** → selects online player.
2. Target receives invite notification → clicks **[Accept]** or **[Reject]**.
3. If accepted → target becomes Applicant, admin is notified.
4. Admin [audits the application](administration.md#audit-applications) → Accept or Refuse.

**Command equivalent (accept/reject):** `/community accept_invitation <communityIdentifier>` | `/community reject_invitation <communityIdentifier>`

---

## Like Community

Clicking **Like** closes the menu and adds one like to the community.

- Each player can like a community **once per day** (based on the server's configured timezone).
- On success, you will see a chat message showing the community's current total likes and its ranking among all communities.
- If you have already liked today, you will be reminded to come back tomorrow.
- In the **Community List Menu**, communities are sorted by total likes in descending order. The community's item tooltip (lore) also displays its like count.

---

## Leave Community

Clicking **Leave** opens a confirmation screen. Confirming removes you from the community.

- **Owners cannot leave.**
- Admins and Members can leave freely while the community is Active or Recruiting.

**Command equivalent:** `/community leave <communityIdentifier>`

---

> **See also:** [Administration](administration.md) · [Economy](economy.md) · [Teleport](teleport.md) · [Chat](chat.md)
