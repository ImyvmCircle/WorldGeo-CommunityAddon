# Chat

> **Navigation:** [Index](index.md) | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | **Chat** | [Commands](commands.md)

---

Every community has a private **Chat Channel** visible only to its formal members (Owner, Admin, Member). Members can send and read community-internal messages without them appearing in the global chat.

## Opening the Chat Room

Click **Chat Channel** (slot 28, Writable Book) in the [Community Menu](community-menu.md) to open the **Chat Room Menu**.

### Chat Room Menu

| Button | Description |
|---|---|
| **Toggle Chat Channel** | Enable or disable the community chat channel as your default chat destination |
| **View Chat History** | Browse past messages in this community |
| **How to Send Messages** | Shows instructions |

The menu also displays the current **channel status** for this community:

| Status | Meaning |
|---|---|
| **Active** (green) | Your typed messages go to this community's channel |
| **Inactive** (grey) | Your messages go to global chat |
| **Other channel** (yellow) | You have a different community's channel active |

---

## Sending Messages

### Method 1 — Via Chat Channel (recommended)

1. Open the [Community Menu](community-menu.md) → **Chat Channel**.
2. Click **Toggle Chat Channel** to set this community as your active channel.
3. Close the menu and type normally in the chat bar — messages are sent to the community channel.
4. To return to global chat, open the Chat Room Menu again and click **Toggle Chat Channel** to disable it.

> **Note:** You can only have one community channel active at a time. Enabling a new channel automatically disables the previous one.

### Method 2 — Via Command

Send a one-off message without changing your active channel:

`/community chat <communityIdentifier> <message>`

### Toggle Active Channel via Command

`/community chat_channel <communityIdentifier>`

---

## Chat History

Clicking **View Chat History** in the Chat Room Menu (or running the equivalent command) shows the last **20 messages** from the community channel in chat, including the sender's name, role display, and timestamp.

| Role | Display Label |
|---|---|
| Owner (Manor) | Landowner |
| Owner (Realm) | Lord |
| Admin (Manor) | HouseKeeper |
| Admin (Realm) | Steward |
| Member (Manor) | Resident |
| Member (Realm) | Citizen |

---

## Access

Only **formal members** (Owner, Admin, Member) may send or read community chat messages. Applicants and refused players cannot use the chat channel.

---

> **See also:** [Community Menu](community-menu.md) · [Administration](administration.md#announcements)
