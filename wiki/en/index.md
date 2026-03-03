# Community — Wiki

> **Navigation:** **Index** | [Main Menu](main-menu.md) | [Community Menu](community-menu.md) | [Administration](administration.md) | [Region](region.md) | [Economy](economy.md) | [Teleport](teleport.md) | [Chat](chat.md) | [Commands](commands.md)

---

A **Community** is a player organization tied to an exclusive geographic region on the IMYVM server. Communities let players govern shared territory, communicate privately, build a collective economy, and collaborate under a structured membership system.

Run `/community` to open the **Community Main Menu** — the starting point for all community interactions. Every feature is accessible through the GUI chest-style menus; commands are available as an alternative entry point for the same operations.

## Menu Navigation Map

```
/community
└── Community Main Menu
    ├── Browse Communities → Community List Menu
    │   └── [click a community]
    │       ├── (non-member) → Non-Member View  (join / view info)
    │       └── (member)     → Community Menu ──────────────────┐
    ├── Create Community → Community Creation Menu              │
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
| Join Cost | $1,500.00 | $500.00 |
| Activation Path | Operator approval | Recruit 4+ members → Operator approval |

## Community Status

| Status | Who Has It | Summary |
|---|---|---|
| **Recruiting** | Realm only | Gathering members; limited admin access |
| **Pending** | Both | Awaiting operator audit |
| **Active** | Both | Fully operational |
| **Revoked** | Both | Deactivated; all actions blocked |

## Member Roles

| Role | Description |
|---|---|
| **Owner** | Full authority; cannot leave |
| **Administrator** | Delegated admin access (subject to owner-configured privileges) |
| **Member** | Regular member; can donate, view, chat, invite, and leave |
| **Applicant** | Pending membership review |
| **Refused** | Rejected applicant |

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
