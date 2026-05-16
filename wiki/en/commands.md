# Commands

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | **Commands**

---

All community commands use the root command `/community`. The GUI menus are the primary interface; commands provide an alternative or complementary way to trigger the same actions. Timed confirmations and invitation or grant responses are completed through chat buttons.

For `<communityIdentifier>`, either the community's **name** or its **numeric ID** can be used.

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

**Shape types:** `RECTANGLE` · `CIRCLE` · `POLYGON`  
**Community types:** `manor` · `realm`

---

## Joining and Leaving

| Command | Description | Menu Equivalent |
|---|---|---|
| `/community join <communityIdentifier>` | Join a community (or apply, per policy) | Browse Communities → click → Join |
| `/community leave <communityIdentifier>` | Leave a community | Community Menu → Leave |

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
| `/community announcement create <communityIdentifier> <content>` | Create a new announcement | Admin / Owner |
| `/community announcement delete <communityIdentifier> <announcementId>` | Delete an announcement | Admin / Owner |
| `/community announcement list <communityIdentifier>` | List active announcements | Formal member |
| `/community announcement view <communityIdentifier> <announcementId>` | View a specific announcement | Formal member |

---

## Operator Commands

> **Permission level 2 required.**

| Command | Description |
|---|---|
| `/community audit <approve\|deny> <communityIdentifier>` | Approve or deny a community creation request |
| `/community force_active <communityIdentifier>` | Force-activate a community |
| `/community force_revoke <communityIdentifier>` | Force-revoke a community |
| `/community force_delete <communityIdentifier>` | Permanently delete a community |
| `/community announcement op list` | List all announcements across all communities |
| `/community announcement op delete <id> <announcementId>` | Force-delete any announcement |
| `/community treasury deposit <communityIdentifier> <amount> [description]` | Deposit funds into a community treasury (sourced as "Server Admin") |
| `/community treasury withdraw <communityIdentifier> <amount> [description]` | Withdraw funds from a community treasury (sourced as "Server Admin") |

- `<amount>` is in display units (e.g., `100.00` = $100.00).
- `[description]` is an optional note that will appear in the treasury ledger.

---

> **See also:** [Index](index.md) · [Main Menu](main-menu.md)
