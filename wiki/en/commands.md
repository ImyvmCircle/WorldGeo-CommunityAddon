# Commands

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | **Commands**

---

All community commands use the root command `/community`. The GUI menus are the primary interface; commands provide an alternative or complementary way to trigger the same actions.

> **Note:** All **interactive confirmation commands** automatically generated in chat (e.g. confirm creation, confirm rename, accept grant, etc.) use a separate root command `/commun` instead of `/community`.

For `<communityIdentifier>`, you can use either the community's **name** or its **numeric ID**.

> **Name quoting:** Names made up entirely of ASCII letters and digits (a–z, A–Z, 0–9) can be typed directly. Any name containing other characters — spaces, Chinese/Japanese/Korean characters, accented letters, symbols, etc. — **must be enclosed in double quotes** when typed in a command: e.g. `"我的领地"` or `"My Realm"`. This rule applies to both `<communityIdentifier>` and any `<scopeName>` argument. Tab-completion adds the required quotes automatically.

## General Commands

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community` | Open the Community Main Menu | — |
| `/community help` | Display the help message in chat | — |
| `/community list [type]` | List communities filtered by type | Browse Communities |
| `/community query <communityIdentifier>` | Show the region info for a community | Community Menu → Region Info |

**List types:** `all` · `joinable` · `recruiting` · `auditing` · `active` · `revoked`

---

## Region Selection

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community select start` | Enable selection mode | Main Menu → Selection Mode button |
| `/community select stop` | Disable selection mode | Main Menu → Selection Mode button |
| `/community select reset` | Clear all selected points | Main Menu → Reset Selection button |

---

## Community Creation

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community create <shapeType> <communityType> <name>` | Initialize a community creation request | Main Menu → Create Community |
| `/commun confirm_creation <regionId>` | Confirm and finalize the creation | Chat prompt [CONFIRM] button |
| `/commun cancel_creation <regionId>` | Cancel the pending creation request | Chat prompt [CANCEL] button |

**Shape types:** `RECTANGLE` · `CIRCLE` · `POLYGON`  
**Community types:** `manor` · `realm`

---

## Joining and Leaving

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community join <communityIdentifier>` | Join a community (or apply, per policy) | Browse Communities → click → Join |
| `/community leave <communityIdentifier>` | Leave a community | Community Menu → Leave |
| `/community accept_invitation <communityIdentifier>` | Accept a pending invitation | Chat invite [Accept] button |
| `/community reject_invitation <communityIdentifier>` | Reject a pending invitation | Chat invite [Reject] button |

---

## Chat

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community chat <communityIdentifier> <message>` | Send a message to the community channel | — |
| `/ch <communityIdentifier> <message>` | Shorthand alias for `/community chat` | — |
| `/community chat_channel <communityIdentifier>` | Toggle this community as active chat channel | Chat Room Menu → Toggle Channel |

---

## Announcements

| Command | Description | Required Role |
|---|---|---|
| `/community announcement create <id> <content>` | Create a new announcement | Admin / Owner |
| `/community announcement delete <id> <announcementId>` | Delete an announcement | Admin / Owner |
| `/community announcement list <id>` | List active announcements | Any member |
| `/community announcement view <id> <announcementId>` | View a specific announcement | Any member |

---

## /commun Interactive Confirmation Commands

These commands are generated automatically in chat as clickable prompts. You typically do not need to type them manually. All of them use the `/commun` prefix (not `/community`).

### Geometry and Settings Confirmations

| Command | Description |
|---|---|
| `/commun confirm_modification <regionId> <scopeName>` | Confirm a geometry change |
| `/commun cancel_modification <regionId> <scopeName>` | Cancel a geometry change |
| `/commun confirm_setting <regionId>` | Confirm a region setting change |
| `/commun cancel_setting <regionId>` | Cancel a region setting change |
| `/commun confirm_teleport_point_set <regionId> <scopeName>` | Confirm setting a teleport point |

### Community Name Confirmations

| Command | Description |
|---|---|
| `/commun confirm_rename <regionId> <nameKey>` | Confirm a community name change |
| `/commun cancel_rename <regionId> <nameKey>` | Cancel a community name change |

### Treasury Grant Confirmations

| Command | Description | Executed by |
|---|---|---|
| `/commun accept_treasury_grant <regionId>` | Accept a treasury grant on behalf of your community | Owner or eligible admin of the **target** community |
| `/commun decline_treasury_grant <regionId>` | Decline a treasury grant on behalf of your community | Owner or eligible admin of the **target** community |
| `/commun cancel_treasury_grant <regionId>` | Cancel your community's outgoing grant request | Owner or eligible admin of the **source** community |

For treasury grant commands, `<regionId>` is the numeric ID of the **source (initiating)** community.

---

## Operator Commands

> **Permission level 2 required.**

| Command | Description |
|---|---|
| `/community audit <approve|deny> <communityIdentifier>` | Approve or deny a community creation request |
| `/community force_active <communityIdentifier>` | Force-activate a community |
| `/community force_revoke <communityIdentifier>` | Force-revoke a community |
| `/community force_delete <communityIdentifier>` | Permanently delete a community |
| `/community announcement op list` | List all announcements across all communities |
| `/community announcement op delete <id> <announcementId>` | Force-delete any announcement |

---

> **See also:** [Index](index.md) · [Main Menu](main-menu.md)
