# Community — Wiki

> **Navigation:** **Index** | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

A **Community** is a player organization tied to an exclusive geographic region on the IMYVM server. Communities let players govern shared territory, communicate privately, build a collective economy, set internal rules, and collaborate under formal roles.

Players can run `/community` to open the **Community Main Menu**. Common operations are available through chest-style menus; commands serve as quick entry points, and chat confirmation buttons handle timed confirmations.

## Menu Navigation Map

```
/community
└── Community Main Menu
    ├── Browse Communities → Community List Menu
    │   └── [click a community]
    │       ├── (non-member) → Non-Member View  (join / view info)
    │       └── (member)     → Community Menu ──────────────────┐
    ├── Territory → Create Community / Add Scope / Modify Land  │
    └── My Communities  → My Community List Menu                │
                          └── [click] → Community Menu ─────────┘
                                        │
                   ┌────────────────────┴──────────────────────────────┐
                   │              Community Menu Buttons                │
                   │  Region Info · Announcements · Members             │
                   │  Treasury · Settings · Teleport · Scope Teleport   │
                   │  Chat · Donate · Leave · Invite                    │
                   └────────────────────┬──────────────────────────────┘
                                        │ (Owner / Admin only)
                               Administration Menu
                               ├── Rename
                               ├── Manage Members → Member List → Member Details
                               │                                   └── Admin Privileges
                               ├── Audit Applications → Applicant List → Accept / Refuse
                               ├── Announcements → List → Create / View / Delete
                               ├── Region Geometry → Scope List → Modify Shape
                               ├── Region Settings → Scope List → Permission & Rule Settings
                               ├── Teleport Points → Scope List → Set / Toggle / Reset
                               └── Join Policy  (toggled in-menu)
```

## Community Types

| | Manor | Realm |
|---|---|---|
| Scale | Small | Large |
| Base Creation Cost | $5,000.00 | $10,000.00 |
| Member Limit | 4 (default) | Unlimited |
| Min. Members to Activate | — | 4 |
| Join Cost | $450.00 | $500.00 |
| Activation Path | Operator approval | Recruit 4 formal members within 48 hours, then operator approval |

## Community Status

| Status | Who Has It | Summary |
|---|---|---|
| **Recruiting** | Realm only | Gathering members; limited admin access |
| **Pending** | Both | Awaiting operator audit |
| **Active** | Both | Fully operational |
| **Revoked** | Both | Deactivated; all actions blocked |

Manors enter **Pending** after creation confirmation. Operator approval moves them to **Active**; denial or later revocation moves them to **Revoked**.

Realms enter **Recruiting** after creation confirmation. The owner must bring the formal member count to 4 within 48 hours. Once that count is reached, the realm automatically enters **Pending** for operator approval. If recruitment expires, the realm is revoked and the creation fee is refunded to the owner.

## Member Roles

| Role | Description |
|---|---|
| **Owner** | Full authority; cannot leave |
| **Administrator** | Delegated admin access (subject to owner-configured privileges) |
| **Member** | Regular member; can donate, view, chat, invite, and leave |
| **Applicant** | Waiting for audit after applying or accepting an invitation; not a formal member |
| **Refused** | Record state after a denied application; not a formal member |

Formal members are owners, administrators, and members. Chat access, donation access, member lists, teleport free-use counts, and realm recruitment counts use formal members only; applicants and refused applicants are excluded.

## Contents

| Page | Topics |
|---|---|
| [Main Menu](main-menu.md) | Browsing communities, creating a community, selection mode |
| [Community Menu](community-menu.md) | All member-facing features accessible from the community hub |
| [Administration](administration.md) | Admin panel, member audit, announcements, admin privileges |
| [Region](region.md) | Scopes, geometry modification, permission & rule settings |
| [Economy](economy.md) | Creation costs, join costs, treasury, donations |
| [Teleport](teleport.md) | Setting and using community teleport points |
| [Chat](chat.md) | Private community chat channel |
| [Commands](commands.md) | Full `/community` command reference |

---

> **See also:** [Commands](commands.md)
