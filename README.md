# README

## Introduction

This Project is designed to constitute a comprehensive framework for players' **communities** in Minecraft servers. It is built on the IMYVMWorldGeo as an extension, which provides mechanics centered around **Region**, performing a Minecraft geography element, and is intended to offer an administration simulation system granting players the ability to self-govern their in-game regions in the form of a player community.

## Major version 1.1.x

This major version introduces community economical profits. 

### 1.1.0

- chore: upgrade to MC 26.1

## Features

### Community

A **community** is a player organization linked to a valid and exclusive region in the IMYVMWorldGeo. A community organization is composed of members, a join policy and a status. For certain community, a council may be enabled.

#### Types

Players can establish two types of communities, manors (small-scale) or realms (large-scale). Realms have higher entry requirements but offer a **substantially higher ceiling**. Unlike manors, where expenses **surge** over time, realms benefit from decreasing marginal development costs as they grow.

**Manors** are small-scale communities with:
- Lower creation costs and requirements;
- **Member capacity limit** (default: 4 members, configurable);
- Suitable for small groups and focused cooperation.

**Realms** are large-scale communities with:
- Higher creation costs and minimum member requirements;
- **No member capacity limit** - can grow indefinitely;
- Economies of scale benefit larger populations.

#### Status

Every community, whether a modest **Manor** or a sprawling **Realm**, exists in a specific state that dictates what players can do. 

`RECRUITING` is a unique status to realms, during which a realm may gather sufficient members to enter into the next stage, or it will be revoked. At this stage, only limited administration is allowed: the owner may appoint administrators and adjust the join policy, while both owner and administrators may review membership applications. Members who joined the community may also leave it.

`PENDING` is a status where the community request meets the automatic requirements and waits for an OP to audit it. The administrative power of a pending community is the same as that of a recruiting community.

In `ACTIVE` status, all available administration power of a community is unlocked, and may be exercised freely. A council may be called by the owner to act as an agent.

If a community is in `REVOKED` status, however, its owner and members is restricted from all actions, including leave it. OPs may adopt an approach to handle it in time, whether making it an eligible community again or delete it.

Technically, types and status are combined as one parameter of a community.

### Community Creation

To create a community, a player who desires to be the founder and the owner may initialize a request, and if it passes checks, recruitment process and audition, an active community owned by the player will be added to the Minecraft Server.

#### Request Initialization

A **community creation request** may be initialized spontaneously by any player trying to inaugurate one, providing

- that the player is not at the time of application, **a formal member (owner, admin, or member — applicants and refused applicants are excluded) of any other community of the same *community type***, which may be chosen when initiating the creation request. Note that communities in **REVOKED** status still count toward this restriction — a player who formally belongs to a revoked community cannot create another community of the same type until they leave;

* that the player delineates a **valid region(scope) prototype**, which shall be understood as an area defined by 
    * a set of **points projected on (x,z) plane**, and these points are selected by right-clicking blocks in the Minecraft world while holding a **Nether Star** (the selection tool provided by IMYVMWorldGeo); left-clicking with the Nether Star removes the most recently placed point (one step undo); this is when the player has already entered **selection mode**, which
        * is defined by IMYVMWorldGeo and utilized by its API;
        * may be started for the player themselves by using the command `/community select start`, and stopped by using the command `/community select stop`; and
        * may be toggled by clicking the **Selection Mode** button in the **Community Creation** screen (reached via the **Territory Menu**); and
    * a **`GeoShapeType`**, 
        * whose value range contains `RECTANGLE`, `CIRCLE` and `POLYGON`;
        * defines the meaning of the points selected above,
            * that `RECTANGLE` means the first of two points are diagonal points of the area; 
            * that `CIRCLE` means the first of two points are the center and a point on the circumference of the circle; and
            * that `POLYGON` means all points are vertices of the polygon in order;
        * may also  be chosen when initiating the creation request after the point selection; and
    * a set of **rules** executed to check whether the combination of points and type is valid when initiating the creation request, and their details are provided by IMYVMWorldGeo; and
* that the player **possesses sufficient in-game currency** to cover the **community creation fee** for the specified *community type*, which consists of:
    * **Base Cost**:
        * that a `MANOR` is charged 5,000.00 by default; and
        * that a `REALM` is charged 10,000.00 by default.
    * **Area-Based Fee** — uses **tiered bracket pricing** (progressive, like a tax bracket system):
        * Each tier covers a range 4× wider than the previous; the multiplier doubles each tier (×1, ×2, ×4, …);
        * Only the area **within each bracket** is charged at that bracket's rate — reaching a higher tier does not retroactively affect lower tiers;
        * **Manor** (first 10,000 m² free, base rate 200 per 10,000 m²):
            * Tier 1: \[10,000, 40,000\) m² at ×1 rate;
            * Tier 2: \[40,000, 160,000\) m² at ×2 rate; and so on;
        * **Realm** (first 40,000 m² free, base rate 600 per 40,000 m²):
            * Tier 1: \[40,000, 160,000\) m² at ×1 rate;
            * Tier 2: \[160,000, 640,000\) m² at ×2 rate; and so on;

When criteria above are achieved, a player may **initialize the creation request**, and the player

- defines the `Community Name`, `Community Type` and `GeoShapeType` in this step;
- may use the command `/community create <geoShapeType> <communityType> [communityName]`; and
- may open the **Territory Menu** by clicking the `圈地` (Territory) button (slot 13) in the Main Menu, then click `创建聚落` (Create Community), toggle on selection mode, select points, then confirm the community name and type. The creation screen integrates point selection directly — no separate selection mode setup needed in the main menu.

#### Automatic Inspection and Proto-Community

Once the request is sent, it will undergo the automatic inspections certificating conditions mentioned in order. Violations of these conditions may be reported by a message sending to the player. And if the request passed the inspections,  the player executing the process is charged, and a **proto-community** is created. Whether the proto-community becomes a **pending community** immediately is also decide by the community type. Whereas a realm stays in the recruiting status until it reaches the minimum requirement of realm population, which is, by default, 4 players, a manor becomes a **pending manor** directly. And a realm needs to recruit sufficient player in 48 hours(in reality) after executing the community request initialization, or it will become a revoked community, and the creation fee will be refunded, Once a realm reaches the population requirement, it also becomes a **pending realm**.

OPs possess the permission **auditing the proto-communities**. If not passes, a community is revoked. Conversely, it becomes an **active community**.

### Community Administration

A community's owner and administrators possess extensive management capabilities accessible through the **Community Administration** button in the community menu, which opens the **Community Administration Menu**:

- **Manage Members** - Access comprehensive member management tools;
- **Community Audit** - Review and process membership applications when join policy requires approval;
- **Announcement** - Post announcements to all members;
- **Treasury Grant** - Send coins from this community's treasury to another community's treasury;
- **Advancement** *(planned)* - Manage community achievements and progression systems;
- **Community Name** - Rename the community globally or rename individual Geoscopes, via an anvil interface; each name carries a 30-day cooldown and a fee ($2,000 for the global name, $100 per Geoscope name);
- **Region Geometry** - Modify the community's geographical boundaries and shape;
- **Region Settings** - Adjust properties and rules for the community's region;
- **Teleport Points** - Create and manage teleportation destinations within the region; and
- **Join Policy** - Toggle between `OPEN`, `APPLICATION`, or `INVITE_ONLY` policies, displayed in the menu as green, yellow, or red respectively.

#### Permission System

The community implements a comprehensive permission system that governs what operations members can perform based on their **role**, the **community status**, and **owner-configured permission toggles**.

##### Permission Hierarchy

**OWNER (Full Authority)**
- Possesses unrestricted access to all administration functions regardless of permission settings;
- Can transfer ownership to another member (excluding APPLICANT or REFUSED) *(planned)*;
- Can toggle each administrator's individual privileges through the member management menu;
- **Cannot** promote, demote, or remove themselves;
- **Cannot** quit the community.

**ADMINISTRATOR (Delegated Authority)**
- Granted access to administration menus and operations, subject to:
  - Community must be in `ACTIVE` status;
  - Specific permission must be enabled by the owner (if applicable);
- **Cannot** execute operations disabled by the owner;
- **Cannot** rename the community without the owner enabling the `RENAME_COMMUNITY` privilege;
- **Cannot** promote, demote, or remove the owner, other administrators, or council members;
- **Can** manage regular members (MEMBER role only);
- **Can** quit the community.

**COUNCIL (Collective Governance)**
- Executes operations through passed votes when council is enabled;
- Subject to the same permission toggles as administrators;
- **Cannot** execute operations disabled by the owner;
- **Cannot** promote, demote, or remove the owner;
- **Cannot** remove other council members;
- Requires simple majority (yea > nay) for vote enactment.

**MEMBER (Basic Participation)**
- May view community information and announcements;
- May donate to the community treasury;
- May be designated as council member for governance participation;
- **Cannot** access administration functions unless designated as council member;
- **Can** quit the community.

**APPLICANT / REFUSED (No Access)**
- Players with these roles cannot perform any community operations;
- **APPLICANT** status indicates pending membership review;
- **REFUSED** status indicates rejected application;
- When attempting to access a community, these players see status pages explaining their situation.

##### Status-Based Restrictions

**Proto-Community (RECRUITING or PENDING)**
- Only recruitment-related operations permitted:
  - Change join policy (OWNER or ADMIN);
  - Audit new member applications (OWNER or ADMIN);
  - Manage members - appoint/demote administrators (OWNER only);
  - Quit the community (all members except OWNER);
- All other administration functions restricted until community becomes ACTIVE:
  - Rename community;
  - Post announcements;
  - Configure advancement system;
  - Modify region geometry or settings;
  - Manage teleport points.

**Active Community (ACTIVE_MANOR or ACTIVE_REALM)**
- Full administration capabilities unlocked for members with appropriate roles;
- Permission system enforced based on role and owner-configured toggles.

**Revoked Community (REVOKED_MANOR or REVOKED_REALM)**
- **All operations prohibited** for all roles;
- Members cannot leave, administrators cannot manage;
- Only server operators can restore or delete the community.

##### Owner Permission Toggles

Owners may individually enable or disable administration operations **per administrator** through the **Manage Privileges** button in each administrator's member page:

- `RENAME_COMMUNITY` - Modify community name;
- `MANAGE_MEMBERS` - Promote, demote, remove members;
- `AUDIT_APPLICATIONS` - Review membership applications;
- `MANAGE_ANNOUNCEMENTS` - Create and delete announcements;
- `GRANT_COINS_FROM_TREASURY` - Initiate and accept treasury grants to/from other communities;
- `MANAGE_ADVANCEMENT` - Administer achievement systems *(planned)*;
- `MODIFY_REGION_GEOMETRY` - Alter community boundaries;
- `MODIFY_REGION_SETTINGS` - Adjust region properties;
- `MANAGE_TELEPORT_POINTS` - Create teleportation destinations;
- `CHANGE_JOIN_POLICY` - Toggle join policy settings.

By default, all privileges are enabled for each administrator. Owners may selectively disable specific operations per administrator, maintaining their own full access at all times.

##### Administrative Notifications

All administrative actions automatically notify relevant parties through a comprehensive notification system:

**Officials Notification** - Sent to owners, administrators, and council members (excluding the executor):
- Member management actions (promote, demote, remove, appoint councilor);
- Membership applications and audit decisions (accept, reject);
- Invitation workflow events (send, accept, reject, audit);
- Join and leave events (including OPEN policy joins);
- Community setting changes (join policy, council toggle, rename);
- Regional modifications (geometry changes, teleport point operations, setting adjustments).

**Target Player Notification** - Sent directly to affected players:
- Role changes (promotion, demotion, councilor appointment);
- Membership status changes (accepted, rejected, removed);
- Invitation events (received, audit results).

**Notification Delivery**:
- Online players: Immediate in-game message with formatted MOTD codes;
- Offline players: Persistent mail delivered upon login;
- All notifications include action details, executor name, and timestamp.

#### Geographic Functions

The **Community Region Scope Menu** provides tools for managing geographical aspects of the community, accessible through various administration functions. Four types of geographic operations are available:

- `GEOMETRY_MODIFICATION` - Alter the shape and boundaries of community regions;
- `SETTING_ADJUSTMENT` - Apply region-wide settings and rules;
- `TELEPORT_POINT_LOCATING` - Designate new teleportation points within the region; and
- `TELEPORT_POINT_EXECUTION` - Teleport to previously established destinations.

Communities support both **global** settings that apply to the entire region, and **local** settings for named sub-scopes within the community (managed through the `geometryScope` list). Each scope is represented visually in the menu interface with distinct items such as armor trim templates and item frames.

#### Permission Settings

Administrators can configure **permission settings** to control what actions players can perform within the community's region or its sub-scopes. The following permission types are supported:

- **Build/Break** - Controls whether players can place or break blocks;
- **Building** - Controls block placement specifically;
- **Breaking** - Controls block breaking specifically;
- **Bucket Placement** - Controls bucket liquid placement;
- **Bucket Scooping** - Controls bucket liquid scooping;
- **Interaction** - Controls general block and entity interaction;
- **Container Access** - Controls whether players can open chests, furnaces, and other containers;
- **Redstone** - Controls redstone component interaction;
- **Villager Trading** - Controls whether players can trade with villagers;
- **PvP Combat** - Controls player-vs-player combat;
- **Animal Killing** - Controls killing of passive animals;
- **Villager Killing** - Controls killing of villagers;
- **Throwable Use** - Controls use of throwable items;
- **Egg Throwing** - Controls throwing eggs;
- **Snowball Throwing** - Controls throwing snowballs;
- **Potion Throwing** - Controls throwing potions;
- **Farming** - Controls crop harvesting and replanting;
- **Igniting** - Controls use of flint and steel and fire charges;
- **Armor Stand Use** - Controls interaction with armor stands;
- **Item Frame Use** - Controls interaction with item frames.

Settings can be applied **globally** (affecting all members) or **per-player** (overriding only for a specific member). When a setting differs from the game's default behavior, a fee is charged based on the affected area and permission type.

When a global region-level permission setting is applied that deviates from the game default, the system automatically grants each current formal member a personal exemption restoring the default value — preserving their existing access. The same exemption is also granted to new members joining a community whose settings already deviate from the default. This automatic exemption does not apply to PvP or fly permissions.

#### Rule Settings

Administrators can configure **rule settings** that affect game mechanics within the community's region or its sub-scopes. Rule settings are always global and cannot be applied per-player:

- **Monster Spawning** - Controls whether monsters spawn in the territory;
- **Phantom Spawning** - Controls whether phantoms spawn in the territory;
- **TNT Block Protection** - Controls whether TNT can destroy blocks in the territory.

Rule modifications follow the same confirmation and cost structure as permission settings, but the cost never includes a per-player factor.

#### Effect Settings

Effect settings (applying potion effects to players within the territory) are planned for a future update.

##### Setting Modification Process

1. Navigate to **Region Settings** via the administration menu, then select a scope or the region-wide option;
2. Choose whether to apply settings globally or for a specific player (player-specific mode only shows permission settings);
3. Click the setting toggle button to see the change, cost breakdown, and area;
4. Review the **Setting Modification Confirmation** showing the exact cost calculation;
5. Confirm to apply the change or cancel to abort. The operation expires in 5 minutes.

##### Pricing

Setting modifications are charged from community assets using **tiered bracket pricing**:

- **Area-based fee** uses progressive brackets; each tier has a linear multiplier (×1, ×2, ×3, …) applied only to the area within that bracket;
- Tier boundaries are based on the community's free area threshold (manor: 10,000 m², realm: 40,000 m²): Tier 1 covers \[0, free area\), Tier 2 covers \[free area, 4×free area\), and so on;
- **Player-specific** settings cost 20% of the global rate;
- **Restoring a setting to its default value** is refunded at 50% of the original cost.

The base rate per 10,000 m² for each setting type:

| Setting | Base Rate |
|---|---|
| Build+Break | 200.00 |
| Interaction | 150.00 |
| Build | 125.00 |
| Break | 125.00 |
| PvP | 125.00 |
| Container | 100.00 |
| Redstone | 100.00 |
| Villager Killing | 100.00 |
| Animal Killing | 70.00 |
| Monster Spawning (rule) | 450.00 |
| Phantom Spawning (rule) | 70.00 |
| TNT Block Protection (rule) | 70.00 |
| Enderman Block Pickup (rule) | 70.00 |
| Sculk Spread (rule) | 70.00 |
| Snow Golem Trail (rule) | 70.00 |
| Dispenser (rule) | 70.00 |
| Pressure Plate (rule) | 70.00 |
| Piston (rule) | 70.00 |
| Farming | 35.00 |
| Armor Stand | 35.00 |
| Item Frame | 35.00 |
| Trade | 30.00 |
| Bucket Build | 20.00 |
| Bucket Scoop | 20.00 |
| Throwable | 20.00 |
| Ignite | 20.00 |
| Potion Use | 10.00 |
| Wind Charge Use | 8.00 |
| Egg Use | 5.00 |
| Snowball Use | 5.00 |

Upon successful modification, all formal members (owner, admins, and members) are notified.

##### Auto-Grant on Member Join

When a new member joins or is accepted into a community that has global permission restrictions (settings differing from default), the system automatically grants that member a player-specific permission entry with the default value, ensuring they are not unintentionally affected by blanket restrictions intended for the general public. This only applies at the Region level and only if no player-specific setting already exists for that member.

#### Territory Modification

Community administrators may alter the geographical boundaries of their territory's administrative districts (scopes) through two entry points: the **Territory Menu** (`圈地`) from the Main Menu, or the **Region Geometry** function in the Administration Menu. Both allow expanding, contracting, or reshaping territorial claims after initial creation.

##### Modification Process (via Territory Menu)

The **Territory Menu** (slot 13, `圈地` button in Main Menu) provides a unified hub for land-claiming operations:

- **创建聚落 (Create Community)** — opens the community creation flow (see Community Creation section);
- **增加辖区 (Add Scope)** — adds a new scope to an existing community; and
- **修改地块 (Modify Territory)** — modifies the geometry of an existing scope.

**Modifying an existing scope:**

1. Click `修改地块` in the **Territory Menu**;
2. If the player is already in **ModifyExisting** selection mode (i.e. has previously chosen a scope to modify), the modification executes immediately with the current selection;
3. If not in selection mode, the player selects a community (if in multiple), then selects a scope from the list. Clicking a scope **starts ModifyExisting selection mode** and closes the menu with a prompt;
4. The player then selects new boundary points in the world (hold **Nether Star**, right-click to place a point, left-click to undo the last point);
5. Re-opening the Territory Menu → `修改地块` executes the modification. A cost summary is shown in chat with an interactive **[CONFIRM]** / **[CANCEL]** prompt valid for 5 minutes.

> **Note:** If the player is in Normal (creation) selection mode when entering `修改地块`, the menu closes with a warning to exit selection mode first.

> **Note:** Requires the **Region Geometry** admin privilege. The community must be in `ACTIVE` status; proto-communities (RECRUITING or PENDING) cannot modify scope geometry.

**Adding a new scope:**

1. Click `增加辖区` in the **Territory Menu**;
2. Select a community if in multiple; permission and status are checked;
3. The **Scope Creation Screen** opens — same design as the community creation screen with integrated selection mode toggle, shape cycle, and a confirm button (visible when ≥2 points are selected).

> **Note:** If the player is in ModifyExisting selection mode when entering `增加辖区`, the menu closes with a warning to complete or cancel the modification first.

> **Note:** Requires the **Region Geometry** admin privilege. The community must be in `ACTIVE` status; proto-communities (RECRUITING or PENDING) cannot add new scopes.

##### Modification Process (via Administration Menu)

The existing **Region Geometry** button in the Administration Menu (slot 19) still works. Selecting a scope from the list now starts **ModifyExisting** selection mode for that scope (instead of opening a geometry menu directly). The player selects new points, then re-opens the territory interface to confirm the change.

**Smart Return on `/community`:** When a player is currently in selection mode that was started from the community menu (either for scope creation or scope geometry modification), running `/community` bypasses the main menu and opens the relevant interface directly — the **Scope Creation Screen** for creation contexts, or the **Scope List** (`GEOMETRY_MODIFICATION` view) for modification contexts. This state is tracked per player and automatically cleared when selection mode ends. If the community data is no longer available, the main menu opens as fallback.

The **Add Administrative District** button in the Administration Menu's global geometry panel now uses the same scope creation flow as `增加辖区` in the Territory Menu, including the selection mode conflict check.

##### Administrative District Addition

Adding a new administrative district uses the dedicated **Scope Creation Screen** with district naming and shape toggle controls, integrated with selection mode.

- Creation cost = **fixed district fee** + **area-based fee**;
- Fixed fee defaults: **Manor $500.00**, **Realm $1,000.00** (configurable);
- Area-based fee follows the same pricing model as area expansion;
- **Soft scope limit**: the recommended maximum number of Geoscopes per community is `ceil(formal_members / 2)` (e.g., 1 member → 1, 2 members → 1, 3 members → 2). Exceeding this limit does **not** block creation; instead, a **soft-limit surcharge** is applied: the entire creation cost (fixed fee + area fee + settings adjustments) is multiplied by **1.5^N**, where N is the number of scopes above the limit. For example, with limit 2 and 2 existing scopes, adding a 3rd (N=1) multiplies the total by 1.5×; adding a 4th (N=2) multiplies by 2.25×. **Important**: this surcharge affects only the scope *creation* cost and has no effect on area-based charges or refunds from modifying the geometry of existing scopes (those use cumulative total area calculations independently). When a surcharge applies, a notice is shown before the confirmation dialog, the confirmation breakdown shows the full formula (e.g., `rawTotal × 1.5^N = adjustedTotal`), and the final deduction message notes the surcharge amount;
- Confirmation is required through a pending command-based confirmation flow;
- Final execution checks:
  - community assets;
  - geometry validity and overlap constraints.

On success, assets are deducted and a notification mail is sent to all formal members (owner/admin/member).

##### Administrative District Sale (Selling a Scope)

The **Sell Administrative District** button in the Administration Menu's global geometry panel allows selling a Geoscope to the system. The refund is calculated as if the scope area were reduced to zero, applying the standard 50% refund rate for land cost and setting cost differences. The scope creation base cost is not refunded.

- The community must retain at least one Geoscope; attempting to sell the last remaining scope is rejected;
- Confirmation is required through the standard pending command-based confirmation flow (5-minute expiry);
- The confirmation display shows a bracket-by-bracket refund breakdown, setting item cost changes, total refund amount, and community asset changes;
- On confirmation, the scope is deleted and the refund is deposited to the community owner's account;
- A notification is sent to all formal members.

##### Pricing System

Territory modifications incur costs or provide refunds based on area changes, using the same **tiered bracket pricing** as creation:

**Expansion Costs**
- Only the *incremental area* between old and new total is priced bracket-by-bracket;
- **Manor**: base rate 200 per 10,000 m², geometric tier multipliers (×1, ×2, ×4, …);
- **Realm**: base rate 600 per 40,000 m², same bracket structure;

**Contraction Refunds**
- The *reduced area* is refunded at 50% of its equivalent expansion cost, bracket-by-bracket;
- Area within the free area threshold (10,000 m² for manors, 40,000 m² for realms) has no charge and no refund value;
- Selling a scope to the system applies the same refund calculation (as if the scope area were reduced to zero), plus refunds for affected settings. The scope creation base cost is not refunded.

**Confirmation Display**
Before execution, a bracket-by-bracket breakdown is shown for each tier involved, including the tier number, bracket range, area within that bracket, unit rate ($/m²), and partial cost (or refund).

##### Requirements

**For Expansions:**
- Community treasury must contain sufficient funds to cover the expansion cost;
- If assets are insufficient, the modification will be rejected.

**For All Modifications:**
- The new boundaries must be geometrically valid;
- The modified territory cannot conflict with other communities' regions;
- All modification requests are validated before execution.

##### Notifications

After a successful modification:

- The change is recorded in the community's activity log;
- An announcement is automatically sent to all community members, informing them of:
  - Which administrative district was modified;
  - How much the area changed;
  - The financial impact on community assets.

#### Community Name Modification

Administrators with the `RENAME_COMMUNITY` privilege (owners are always exempt) may rename the community's overall name or any individual Geoscope name. This is accessed via the **Community Name** button (slot 19, name tag icon) in the Administration Menu.

Clicking it opens the **Scope Selector** where the player chooses either:
- **Global** — renames the community's top-level name; or
- A specific **Geoscope** — renames that Geoscope only.

After selection, an **anvil interface** appears with the current name pre-filled. Enter the new name and take the output item to proceed.

**Costs and cooldowns:**

| Target | Cost | Cooldown |
|---|---|---|
| Global community name | $2,000.00 | 30 days (real-time) per community |
| Individual Geoscope name | $100.00 | 30 days (real-time) per Geoscope |

If a name was changed within the last 30 days, the operation is blocked immediately when the rename button is clicked (before the anvil opens), and the remaining cooldown days are shown. The cooldown is tracked independently per name key (global for the community name, or the current Geoscope name after each rename) and persists across server restarts.

After entering the new name and confirming payment, an **interactive billing confirmation** is sent in chat:
- `[CONFIRM]` — executes the rename and deducts assets;
- `[CANCEL]` — aborts the operation.

The confirmation prompt expires after 5 minutes.

**Command equivalents:**
- `/_commun confirm_rename <regionId> <nameKey>` — confirm a rename
- `/_commun cancel_rename <regionId> <nameKey>` — cancel a rename

#### Member Management

Through the **Manage Members** function, administrators access the **Community Member List Menu**, which displays:

- The owner in a dedicated slot;
- Administrators in a separate section; and
- Regular members with pagination (35 members per page).

Selecting an individual member opens the **Community Member Menu**, which provides:

- Member profile display;
- **Settings** - Configure player-specific regional restrictions and permissions (distinct from region-wide settings);
- **Remove** - Expel the member from the community;
- **Message** - Send notifications through the internal mail system; and
- **Promote** - Elevate a member to administrator status (owner only).

#### Audit System

When the join policy is set to `APPLICATION`, membership requests must be reviewed through the **Administration Audit List Menu**, accessible via **Community Audit** in the administration menu. For each pending applicant, administrators may view the applicant's profile and request details in the **Administration Audit Menu**, then choose to either **Accept** the application to grant membership or **Refuse** it to set their status to `REFUSED`.

**Manor Capacity Enforcement:** When approving an application for a manor community, the system checks whether the community has reached its member capacity (default: 4 members, configurable). If the manor is at capacity, the approval is blocked and the administrator receives a notification indicating the limit has been reached. The applicant's status remains unchanged as `APPLICANT`, ensuring the member limit is never exceeded during the audit process.

#### Invitation System

Communities may proactively recruit members through the **invitation mechanism**, allowing eligible members to invite online players directly. This system offers an alternative to passive application-based recruitment.

##### Sending Invitations

Members with sufficient permissions may invite online players through:

- The **Invite Member** button in the Community Menu (interaction section);
- Upon clicking, the **Online Player List Menu** displays all currently online players (excluding existing members);
- Selecting a player initiates an invitation, which sends an interactive chat message to the target player.

**Invitation Requirements:**
- Inviter must be a member with a role other than `APPLICANT` or `REFUSED`;
- Community must have sufficient assets to cover the join cost (deducted upon audit approval):
  - Manor join cost: 450.00 (configurable);
  - Realm join cost: 500.00 (configurable);
- Target player must meet all standard joining conditions (not already a member, passes membership checks);
- **Manor capacity check**: Manor communities must not be at member capacity (default: 4 members). If the manor is at capacity, the invitation cannot be sent and the inviter is notified.

##### Receiving Invitations

When invited, the target player receives an interactive chat message containing:
- Invitation details (inviter name and community name);
- **[Accept]** button (green) - Click to accept the invitation;
- **[Reject]** button (red) - Click to decline the invitation.

The recipient may respond by:
- Clicking the interactive buttons directly in chat; or
- Using commands:
  - `/_commun accept_invitation <communityIdentifier>` - Accept the invitation (where `communityIdentifier` can be the community name or region ID);
  - `/_commun reject_invitation <communityIdentifier>` - Reject the invitation.

**Invitation Response:**
- **Accepting** - The player is added to the community as an `APPLICANT` with a special `isInvited` flag, pending administrator audit. The invitee receives confirmation and is instructed to check the community list for audit status. The invitation remains active until audit decision is made;
- **Rejecting** - The invitation is discarded immediately, the player is removed from the pending applicant list, and the inviter is notified of the rejection;
- **Timeout** - Invitations expire after 5 minutes (configurable via `INVITATION_RESPONSE_TIMEOUT_MINUTES`). When an invitation expires:
  - The invited player is automatically removed from the applicant list;
  - Both the inviter and invitee receive expiration notifications if they are online;
  - Expired invitations cannot be accepted or rejected.

##### Invitation Audit Process

Invited players follow a modified audit workflow:

**Upon Acceptance:**
1. Player is granted immediate access as a pending member with `isInvited` flag set to `true`;
2. Administrator reviews the application through the standard **Community Audit** interface;
3. If **approved**:
   - Community pays the join cost from its assets (not the invited player);
   - Join cost is recorded as an expenditure in the community's financial records;
   - Member role is upgraded to `MEMBER`, and `isInvited` flag is cleared;
   - If community assets are insufficient, audit approval fails with an error message;
4. If **refused**:
   - Player is immediately removed from the community (unlike standard applicants who are marked as `REFUSED`);
   - No join cost is deducted.

**Asset Accounting:**
- Community assets are calculated as: Total Income (donations) - Total Expenditures;
- Expenditures are tracked separately in the `expenditures` ArrayList;
- Each expenditure records:
  - `amount` - The join cost paid;
  - `timestamp` - The time of payment;
- This ensures transparent financial tracking for invitation costs versus donation income.

##### Technical Details

**Unified PendingOperation System:**
- Invitations are stored in the same `pendingOperations` map as community creation requests;
- Uses invitee UUID's hashCode as the key for invitation operations (mirroring how region ID is used as key for community operations);
- `PendingOperation` includes optional parameters for invitation-specific data:
  - `inviterUUID` - The UUID of the player who sent the invitation;
  - `inviteeUUID` - The UUID of the invited player;
- The target community is found by searching for a member with matching UUID and `isInvited=true` flag (no redundant storage);
- Integration with existing `PendingCheck` system handles automatic expiration;
- Follows the exact same storage pattern as community creation: **key carries the identifier, not stored redundantly inside the object**.

**MemberAccount Attributes:**
- `isInvited` (Boolean) - Flags whether the member joined via invitation (default: `false`);
- Invited members with `APPLICANT` status are treated differently during audit to ensure community-funded membership.

**Configuration Options:**
- `INVITATION_RESPONSE_TIMEOUT_MINUTES` - Time limit in minutes for responding to invitations (default: 5 minutes);
- `COMMUNITY_JOIN_COST_MANOR` - Join cost for manor communities paid by the community when invitation is approved (default: 450.00);
- `COMMUNITY_JOIN_COST_REALM` - Join cost for realm communities paid by the community when invitation is approved (default: 500.00);
- Timeout enforcement is automatic through the pending operations system, which checks every `PENDING_CHECK_INTERVAL_SECONDS`.

**Permission System:**
- Invitation privileges are governed by the member's role (not `APPLICANT` or `REFUSED`);
- No separate permission toggle exists—all eligible members may invite;
- Invitations must be sent to online players only.

**Commands:**
- `/_commun accept_invitation <communityIdentifier>` - Accept a pending invitation to join a community;
- `/_commun reject_invitation <communityIdentifier>` - Reject a pending invitation to join a community;
- `/community chat <communityIdentifier> <message>` - Send a message to the community chat room;
- `/community chat_toggle <communityIdentifier>` - Toggle channel mode on/off (when enabled, all messages go to community chat).

### Membership

#### Member Roles

Communities implement a hierarchical member structure with five distinct roles:

- `OWNER` (0) - The founder and ultimate authority of the community, with full administrative powers;
- `ADMIN` (1) - Administrators delegated by the owner to manage community affairs;
- `MEMBER` (2) - Standard members with basic participation rights;
- `APPLICANT` (3) - Players who have submitted applications pending review; and
- `REFUSED` (4) - Players whose applications have been rejected.

#### Member Account

Each member account maintains the following attributes:

- `joinedTime` - Timestamp of when the member joined the community;
- `basicRoleType` - The member's current role within the hierarchy;
- `isCouncilMember` - Designation as a council member for governance purposes;
- `governorship` - Custom governance value for advanced features;
- `mail` - An internal notification system (ArrayList) for community communications;
- `turnover` - Donation history tracking all contributions to the community; and
- `isInvited` - Flag indicating whether the member joined via invitation (affects audit payment responsibility).

#### Member Interaction *()* 

Members may interact with their community through the **Community Menu**, accessible from the **My Communities** list in the main menu:

- **Description** - View detailed information about the community's region;
- **Announcement** - View community announcements;
- **Members** - Browse the complete member list;
- **Assets** - View and manage community resources and donations;
- **Settings** - Adjust personal community preferences through the **Community Setting Menu**;
- **Teleport to Community** - Teleportation to the community's main scope point with dynamic delay/fee after daily free uses;
- **Teleportation Scope** - Select specific teleportation destinations within the community;

Teleport point accessibility means **public visibility for non-formal players** only. Formal members (Owner/Admin/Member) can use community teleport points regardless of public/private state, while non-formal players can only use teleport points marked as public.

Teleport point setup now uses a confirmation flow with pricing:
- first active teleport point in a community is free;
- creating the second active point costs 2000, and each additional active point doubles that setup cost; and
- modifying an already-set teleport point costs 1000.

Teleport execution now uses daily per-player/per-community escalation:
- formal members have 10 free uses per day; non-formal players have 1 free use per day;
- after free uses, teleport starts at fee 10 and delay 2 seconds, both doubling per additional use that day; and
- moving or taking damage during delay cancels teleport, and insufficient balance at execution time also cancels teleport.

From the public community list, non-members can open a teleport scope list and use any public teleport point directly.
- **Community Chat** *()* - Access community messaging system;
- **Advancement** *()* - View community achievements;
- **Donate to Community** - Contribute currency to the community (opens the Assets menu);
- **Like Community** - Like the community; each player can like once per day (timezone-aware); communities are ranked by total likes;
- **Leave Community** - Exit the community; and
- **Invite Member** - Recruit new members to join by sending invitations to online players.

#### Community Chat Room

The chat room system allows formal community members to communicate with each other through a dedicated channel.

**Access & Permissions:**
- Only formal members (Owner, Admin, Member) can access the chat room
- Applicants and refused members cannot use chat features

**Toggle Modes:**
- **Channel Toggle:** Enable/disable community chat channel (located at center of Chat Room menu)
  - When enabled, all messages typed are sent to community chat instead of global chat
  - When disabled, messages go to global chat as normal

**Message Format & Styling:**

Messages are displayed with colorful, decorative formatting based on member role and community type:

*Manor Format:*
```
[CommunityName] §6Lord§r/§eHouseKeeper§r/§7Citizen§r PlayerName: Message
```

*Realm Format:*
```
[CommunityName] §4Landowner§r/§6Steward§r/§2Resident§r PlayerName: Message
```

Role decorations:
- **Owner**: Lord (Manor) / Landowner (Realm) - bold gold/dark red
- **Admin**: HouseKeeper (Manor) / Steward (Realm) - yellow/gold  
- **Member**: Citizen (Manor) / Resident (Realm) - gray/green

**Chat History:**
- Direct display of last 20 messages when opening Chat Room menu
- Messages show sender name, role decoration, and content
- Automatically scrolls to show most recent messages
- Empty history shows "No messages yet"

**Usage Methods:**
1. **Command-based:** Use `/community chat <community> <message>` to send a single message
2. **Toggle-based:** Enable channel toggle in Chat Room menu or with `/community chat_toggle <community>`, then type normally

**Technical Details:**
- Messages stored in unified `CommunityMessage` storage with type `CHAT`
- Per-member toggle: `chatHistoryEnabled` (default: true)
- Only online members with `chatHistoryEnabled=true` receive broadcasts
- Messages persist in database for history viewing

### Council System

The **Council** is an independent governance system that may be **enabled or disabled by the owner** to facilitate collective decision-making. When enabled, designated council members may participate in voting on significant community matters, acting as an agent with delegated authority subject to owner-configured permission restrictions.

#### Enabling the Council

- Only the community **owner** may enable or disable the council system;
- Council status is stored as the `enabled` flag in the community's council object;
- Council members may be designated by setting `isCouncilMember` to `true` in member accounts;
- Council membership is independent of the basic role hierarchy, allowing regular members to participate in governance.

#### Council Authority

Council members collectively execute administration operations through a voting mechanism:

- Council authority is subject to **owner permission toggles**, identical to administrator restrictions;
- **Cannot** execute operations disabled by the owner;
- **Cannot** promote, demote, or remove the owner;
- **Cannot** remove other council members;
- Must operate through formal votes; individual council members have no unilateral authority.

#### Council Voting

The council implements a formal voting mechanism through the `CouncilVote` class:

- Votes are proposed by council members and track separate `yea` and `nay` vote counts;
- A proposal is marked as `enacted` when yea votes exceed nay votes, implementing a simple majority rule;
- Each vote records the `proposer` UUID, `proposeTime` timestamp, and `enactment` status; and
- Nine distinct `ExecutionType` values are available for voting:
  - `DEFAULT` - General proposals;
  - `RENAME` - Community name changes;
  - `SETTING_CHANGE` - Modifications to community settings;
  - `APPLICATION` - Membership application decisions;
  - `MEMBER_PROMOTION` - Elevation of members to administrator status;
  - `MEMBER_DEMOTION` - Reduction of administrator privileges;
  - `MEMBER_REMOVE` - Expulsion of members;
  - `MEMBER_INVITE` - Invitations to prospective members; and
  - `CHANGE_JOIN_POLICY` - Modifications to the join policy.

All active votes are maintained in a `voteSet` within the council, allowing multiple concurrent proposals.

### Assets System

Communities maintain a treasury that tracks all income and expenditures. Any member (excluding `APPLICANT` or `REFUSED` status) may donate currency to support the community.

#### Donation Process

Members may donate through the **Community Assets Menu**, accessible via:

- The **Assets** button in the Community Menu (description section); or
- The **Donate to Community** button in the Community Menu (interaction section).

The donation menu presents ten predefined amounts. When a member selects an amount:

- The system verifies the member has sufficient currency;
- The currency is deducted from the member's account;
- A `Turnover` record (source: `PLAYER`, key: `community.treasury.desc.donation`) is added to the member's `turnover` list.

#### Asset Inquiry

The **Community Assets Menu** displays:

- **Total Assets** - Net balance: (all member donations + all community income) minus (all expenditures);
- **Donate** button - Opens the donation menu;
- **Donor List** button - Opens the donor list; and
- **View Ledger** button - Opens the treasury ledger.

#### Donor List

The **Donor List Menu** presents all members who have made donations, sorted by total contribution in descending order. Selecting a donor opens the **Donor Details Menu** with individual turnover records.

#### Treasury Ledger

The **Treasury Ledger** is a unified paginated view of all treasury transactions (income and expenditures), sorted by timestamp descending. Income entries display a Gold Ingot icon; expenditure entries display a Red Stained-Glass Pane icon. Each entry shows the amount, source type, description, and timestamp.

#### Technical Implementation

Each member account maintains a `turnover` ArrayList. The community maintains a `communityIncome` ArrayList (grants received, admin deposits, future system income) and an `expenditures` ArrayList. The `Turnover` data class has been extended with `source: TurnoverSource`, `descriptionKey: String?`, and `descriptionArgs: List<String>` for full audit trail support. The `TurnoverSource` enum values are `PLAYER`, `COMMUNITY_GRANT`, `SYSTEM`, `SERVER_ADMIN`, and `UNKNOWN`. Database serialization uses a `-1` magic-number sentinel to distinguish the new rich format from legacy records (backward-compatible). The `getTotalAssets()` computes: `memberDonations + communityIncome.sum - expenditures.sum`.

### Treasury Grant System

The community owner or an administrator with the `GRANT_COINS_FROM_TREASURY` privilege may transfer funds from this community's treasury to another community's treasury using the **Treasury Grant** button in the Administration Menu.

#### Grant Flow

1. The initiator opens the **Treasury Grant Target List Menu** from the Administration Menu;
2. Selects a target community;
3. Selects a grant amount in the **Treasury Grant Amount Menu**;
4. The system dispatches a chat notification with three clickable interaction buttons — **[Accept]**, **[Decline]**, and **[Cancel]** — to all formal members of both communities;
5. An eligible member of the **target** community clicks **[Accept]** to confirm, or **[Decline]** to reject. An eligible member of the **source** community may click **[Cancel]** to withdraw.
6. On acceptance, the amount is deducted from the source community's treasury and credited to the target community's treasury. The operation expires after **5 minutes** if no response is received.

#### Accounting

- A `Turnover` record (source: `COMMUNITY_GRANT`) is appended to the source community's `expenditures`.
- A `Turnover` record (source: `COMMUNITY_GRANT`) is appended to the target community's `communityIncome`.
- Both communities' `getTotalAssets()` results are affected immediately.

#### Technical Implementation

The grant state is tracked via `TreasuryGrantConfirmationData` stored in the source community's `pendingOperations` map under key `sourceRegionId`. Only one pending treasury grant per community is allowed at a time. Eligibility for both initiating and accepting is checked via `isEligibleTreasuryGrantRecipient()`, which requires the `GRANT_COINS_FROM_TREASURY` privilege or the OWNER role. Confirmation commands are registered under the `/_commun` root (not `/community`): `accept_treasury_grant`, `decline_treasury_grant`, and `cancel_treasury_grant`.

### Server Admin Treasury Operations

Server administrators (permission level 2) may directly adjust any community's treasury balance via command:

- `/community treasury deposit <communityIdentifier> <amount> [description]` — adds funds to `communityIncome` with `source: SERVER_ADMIN`.
- `/community treasury withdraw <communityIdentifier> <amount> [description]` — adds an entry to `expenditures` with `source: SERVER_ADMIN`.

`<amount>` is in display units (e.g., `100.00` = $100.00 = 10000L internally). `[description]` is an optional note recorded in the ledger. Both commands save the database immediately.

### Announcement System

Communities possess a comprehensive announcement system enabling administrators to broadcast formatted messages to all members. Announcements support **MOTD (Message of the Day) formatting** with color codes (`&` or `§`) and are tracked with read/unread status for each member.

#### Creating Announcements

Community owners and administrators may create announcements through:

- The **Announcement** button in the Community Administration Menu, which opens the **Announcement Management** interface; or
- The command `/community announcement create <communityId> <content>`, where content supports MOTD formatting.

When creating an announcement via menu:

1. Click **Create Announcement** to open an anvil input interface;
2. Enter the announcement content with optional formatting codes (e.g., `&cImportant: &7Server maintenance tonight`);
3. The system parses the content using `TextParser`, converting formatting codes to styled Text components; and
4. The announcement is immediately broadcast to all online members.

#### Announcement Properties

Each announcement maintains:

- `id` - Unique UUID identifier;
- `content` - Formatted Text with parsed MOTD styling;
- `authorUUID` - The administrator who created it;
- `timestamp` - Creation time (long);
- `isDeleted` - Soft delete flag (hidden from members, visible to operators); and
- `readBy` - Set of UUIDs tracking which members have viewed the announcement.

#### Viewing Announcements

Members access announcements through:

- The **Announcement** button in the Community Menu, opening a list displaying:
  - `[UNREAD]` in yellow for new announcements (writable book icon);
  - `[READ]` in gray for viewed announcements (paper icon);
  - Author name and timestamp;
  - Preview of first 30 characters;
  - Sorted by most recent first; or
- Commands:
  - `/community announcement list <communityName>` - Lists all announcements with status and preview;
  - `/community announcement view <communityName> <announcementId>` - Displays full content and automatically marks as read.

Example command output:
```
--- Announcements for MyRealm (ID: 5) ---
1. [UNREAD] [a1b2c3d4...] Important: Server maintenance tonight...
2. [READ] [e5f6g7h8...] Welcome new members! Please read the...
```

When a member views an announcement, it is automatically marked as read for that player and persisted to the database.

#### Administrator Management

Administrators manage announcements through the **Announcement Management** menu:

- Browse all announcements (including deleted ones for administrators);
- View detailed statistics showing read count (e.g., "Read: 15/23");
- Delete announcements via the **Delete** button, which:
  - Sets `isDeleted` to `true` (soft delete);
  - Hides the announcement from members immediately;
  - Preserves the data for operator auditing; or
- Use commands:
  - `/community announcement create <communityName> <content>` - Create announcement with MOTD formatting;
  - `/community announcement delete <communityName> <announcementId>` - Soft delete an announcement.

Example create command:
```
/community announcement create MyRealm &cImportant: &7Server maintenance tonight at 8pm
```

The content supports standard Minecraft color codes (`&0-9`, `&a-f`) and formatting (`&l` bold, `&o` italic, `&r` reset).

#### Notification System

The announcement system implements a dual notification strategy:

**Online Members** - Immediately notified when announcement created:
- Receive message: `[New Community Announcement]`;
- Announcement content displayed directly in chat.

**Offline Members** - Notified upon login:
- System checks all communities where player is a member;
- Counts unread announcements per community;
- Displays summary: `Community [Name] has N unread announcement(s)`;
- Final total: `You have N unread announcement(s) across all communities`;
- Distinct from the personal mail system, which handles individual member-to-member messages.

#### Operator Functions

OPs possess elevated privileges for cross-community announcement management:

- `/community announcement op list` - Lists all announcements across all communities with counts per community;
- `/community announcement op delete <communityId> <announcementId>` - Permanently force delete any announcement, bypassing administrator permissions;
- Operators may view deleted announcements for auditing purposes;
- All operator actions bypass standard permission checks.

Example operator list output:
```
- MyManor (ID: 3): 2 announcement(s)
- TestRealm (ID: 5): 5 announcement(s)
Total: 7 announcement(s) across all communities.
```

#### Persistence

Announcements are fully persisted in the community database (`iwg_community.db`):

- Content stored as raw string and re-parsed on load to preserve formatting;
- All read status tracked per UUID;
- Soft-deleted announcements retained for operator review;
- Atomic save operations after each create/delete/read action.

### Community Discovery

Players may discover and join communities through the **Community List Menu**, accessible from the main menu via the **List Communities** button. The list provides filtering options to display:

- `ALL` - All communities in the server;
- `JOIN_ABLE` - Communities currently accepting new members;
- `RECRUITING` - Realms in their recruitment phase;
- `AUDITING` - Communities pending operator approval;
- `ACTIVE` - Fully operational communities; and
- `REVOKED` - Communities whose status has been revoked.

Players may browse their own memberships through the **My Communities** button in the main menu.

## Wiki

Comprehensive player guides are available in two languages:

- **English:** [wiki/en/index.md](wiki/en/index.md)
- **中文：** [wiki/zh/index.md](wiki/zh/index.md)

Each wiki covers the full GUI menu system (primary) and command reference (supplementary), including community types, member roles, region/territory management, economy, teleport points, and the chat channel.

> **Command name syntax:** In all `/community` commands, names (community names, Geoscope names) consisting solely of ASCII letters and digits (a–z, A–Z, 0–9) may be entered as-is. Names containing any other character — spaces, Chinese/Japanese/Korean characters, accented letters, symbols, etc. — must be **enclosed in double quotes**, e.g. `"我的领地"`. Autocompletion wraps such names in quotes automatically.

---

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.
