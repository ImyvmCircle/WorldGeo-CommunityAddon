# README

## Introduction

This Project is designed to constitute a comprehensive framework for players' **communities** in Minecraft servers. It is built on the IMYVMWorldGeo as an extension, which provides mechanics centered around **Region**, performing a Minecraft geography element, and is intended to offer an administration simulation system granting players the ability to self-govern their in-game regions in the form of a player community.

## Features

### Community

A **community** is a player organization linked to a valid and exclusive region in the IMYVMWorldGeo. A community organization is composed of members, a join policy and a status. For certain community, a council may be enabled.

#### Types

Players can establish two types of communities, manors (small-scale) or realms (large-scale). Realms have higher entry requirements but offer a **substantially higher ceiling**. Unlike manors, where expenses **surge** over time, realms benefit from decreasing marginal development costs as they grow.

#### Status

Every community, whether a modest **Manor** or a sprawling **Realm**, exists in a specific state that dictates what players can do. 

`RECRUITING` is a unique status to realms, during which a realm may gather sufficient members to enter into the next stage, or it will be revoked. At this stage, only the community name, join policy and the membership be administrated. Member who joined the community may also leave it.

`PENDING` is a status where the community request meets the automatic requirements and waits for a Server operator to audit it. The administrative power of a pending community is the same as that of a recruiting community.

In `ACTIVE` status, all available administration power of a community is unlocked, and may be exercised freely. A council may be called by the owner to act as an agent.

If a community is in `REVOKED` status, however, its owner and members is restricted from all actions, including leave it. Server operators may adopt an approach to handle it in time, whether making it an eligible community again or delete it.

Technically, types and status are combined as one parameter of a community.

### Community Creation

To create a community, a player who desires to be the founder and the owner may initialize a request, and if it passes checks, recruitment process and audition, an active community owned by the player will be added to the Minecraft Server.

#### Request Initialization

A **community creation request** may be initialized spontaneously by any player trying to inaugurate one, providing

- that the player is not at the time of application, **a member of any other community of the same *community type***, which may be chosen when initiating the creation request;

* that the player delineates a **valid region(scope) prototype**, which shall be understood as an area defined by 
    * a set of **points projected on (x,z) plane**, and these points are selected by right-click positions in the Minecraft world with a command block in hand, when the player has already entered **selection mode**, which
        * is defined by IMYVMWorldGeo and utilized by its API;
        * may be started for the player themselves by using the command `/community select start`, and stopped by using the command `/community select stop`; and
        * may be toggled by left-clicking the `Point Selection Mode: {Enabled/Disabled}` button in the box-interface `Community Main Menu`;
    * a **`GeoShapeType`**, 
        * whose value range contains `RECTANGLE`, `CIRCLE` and `POLYGON`;
        * defines the meaning of the points selected above,
            * that `RECTANGLE` means the first of two points are diagonal points of the area; 
            * that `CIRCLE` means the first of two points are the center and a point on the circumference of the circle; and
            * that `POLYGON` means all points are vertices of the polygon in order;
        * may also  be chosen when initiating the creation request after the point selection; and
    * a set of **rules** executed to check whether the combination of points and type is valid when initiating the creation request, and their details are provided by IMYVMWorldGeo; and
* that the player **possesses sufficient in-game currency** to cover the **community creation fee** for the specified *community type*, 
    * that a `MANOR` is charged 15000 by default; and
    * that a `REALM` is charged 30000 by default.

When criteria above are achieved, a player may **initialize the creation request**, and the player

- defines the `Community Name`, `Community Type` and `GeoShapeType` in this step;
- may use the command `/community create <geoShapeType> <communityType> [communityName]`; and
- may left-click the `Create Community` button in the box-interface `Community Main Menu`, set the community information in this step as mentioned, left-click `Confirm Creation` button, and then `Confirm` again.

#### Automatic Inspection and Proto-Community

Once the request is sent, it will undergo the automatic inspections certificating conditions mentioned in order. Violations of these conditions may be reported by a message sending to the player. And if the request passed the inspections,  the player executing the process is charged, and a **proto-community** is created. Whether the proto-community becomes a **pending community** immediately is also decide by the community type. Whereas a realm stays in the recruiting status until it reaches the minimum requirement of realm population, which is, by default, 4 players, a manor becomes a **pending manor** directly. And a realm needs to recruit sufficient player in 48 hours(in reality) after executing the community request initialization, or it will become a revoked community, and the creation fee will be refunded, Once a realm reaches the population requirement, it also becomes a **pending realm**.

Server operators possess the permission **auditing the proto-communities**. If not passes, a community is revoked. Conversely, it becomes an **active community**.

### Community Administration

A community's owner and administrators possess extensive management capabilities accessible through the **Community Administration** button in the community menu, which opens the **Community Administration Menu**:

- **Rename Community** - Modify the community's name through an anvil interface;
- **Manage Members** - Access comprehensive member management tools;
- **Community Audit** - Review and process membership applications when join policy requires approval;
- **Announcement** - Post announcements to all members;
- **Advancement** *()* - Manage community achievements and progression systems;
- **Assets** - Administer community resources and treasury;
- **Region Geometry** - Modify the community's geographical boundaries and shape;
- **Region Settings** - Adjust properties and rules for the community's region;
- **Teleport Points** - Create and manage teleportation destinations within the region; and
- **Join Policy** - Toggle between `OPEN`, `APPLICATION`, or `INVITE_ONLY` policies, displayed in the menu as green, yellow, or red respectively.

#### Permission System

The community implements a comprehensive permission system that governs what operations members can perform based on their **role**, the **community status**, and **owner-configured permission toggles**.

##### Permission Hierarchy

**OWNER (Full Authority)**
- Possesses unrestricted access to all administration functions regardless of permission settings;
- Can transfer ownership to another member (excluding APPLICANT or REFUSED);
- Can enable or disable the council system;
- Can toggle specific administration permissions for administrators and council;
- **Cannot** promote, demote, or remove themselves;
- **Cannot** quit the community.

**ADMINISTRATOR (Delegated Authority)**
- Granted access to administration menus and operations, subject to:
  - Community must be in `ACTIVE` status;
  - Specific permission must be enabled by the owner (if applicable);
- **Cannot** execute operations disabled by the owner;
- **Cannot** change the community name;
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
  - Quit the community (all members except OWNER);
- All other administration functions restricted until community becomes ACTIVE.

**Active Community (ACTIVE_MANOR or ACTIVE_REALM)**
- Full administration capabilities unlocked for members with appropriate roles;
- Permission system enforced based on role and owner-configured toggles.

**Revoked Community (REVOKED_MANOR or REVOKED_REALM)**
- **All operations prohibited** for all roles;
- Members cannot leave, administrators cannot manage;
- Only server operators can restore or delete the community.

##### Owner Permission Toggles

Owners may individually enable or disable administration operations for **administrators** and **council** through permission management menus:

- `RENAME_COMMUNITY` - Modify community name;
- `MANAGE_MEMBERS` - Promote, demote, remove members;
- `AUDIT_APPLICATIONS` - Review membership applications;
- `MANAGE_ANNOUNCEMENTS` - Create and delete announcements;
- `MANAGE_ADVANCEMENT` - Administer achievement systems;
- `MANAGE_ASSETS` - Control community treasury;
- `MODIFY_REGION_GEOMETRY` - Alter community boundaries;
- `MODIFY_REGION_SETTINGS` - Adjust region properties;
- `MANAGE_TELEPORT_POINTS` - Create teleportation destinations;
- `CHANGE_JOIN_POLICY` - Toggle join policy settings.

By default, all permissions are enabled. Owners may selectively disable specific operations to restrict administrator and council authority while maintaining their own full access.

#### Geographic Functions

The **Community Region Scope Menu** provides tools for managing geographical aspects of the community, accessible through various administration functions. Four types of geographic operations are available:

- `GEOMETRY_MODIFICATION` - Alter the shape and boundaries of community regions;
- `SETTING_ADJUSTMENT` - Apply region-wide settings and rules;
- `TELEPORT_POINT_LOCATING` - Designate new teleportation points within the region; and
- `TELEPORT_POINT_EXECUTION` - Teleport to previously established destinations.

Communities support both **global** settings that apply to the entire region, and **local** settings for named sub-scopes within the community (managed through the `geometryScope` list). Each scope is represented visually in the menu interface with distinct items such as armor trim templates and item frames.

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

When the join policy is set to `APPLICATION`, membership requests must be reviewed through the **Administration Audit List Menu**, accessible via **Community Audit** in the administration menu. For each pending applicant, administrators may:

- View the applicant's profile and request details in the **Administration Audit Menu**;
- **Accept** - Approve the application and grant membership; or
- **Refuse** - Deny the application, setting their status to `REFUSED`.

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
  - Manor join cost: 1,500.00 (configurable);
  - Realm join cost: 500.00 (configurable);
- Target player must meet all standard joining conditions (not already a member, passes membership checks);
- Community must not be at member capacity (for manors).

##### Receiving Invitations

When invited, the target player receives an interactive chat message containing:
- Invitation details (inviter name and community name);
- **[Accept]** button (green) - Click to accept the invitation;
- **[Reject]** button (red) - Click to decline the invitation.

The recipient may respond by:
- Clicking the interactive buttons directly in chat; or
- Using commands:
  - `/community accept_invitation <communityIdentifier>` - Accept the invitation (where `communityIdentifier` can be the community name or region ID);
  - `/community reject_invitation <communityIdentifier>` - Reject the invitation.

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

**MemberAccount Attributes:**
- `isInvited` (Boolean) - Flags whether the member joined via invitation (default: `false`);
- Invited members with `APPLICANT` status are treated differently during audit to ensure community-funded membership.

**Configuration Options:**
- `INVITATION_RESPONSE_TIMEOUT_MINUTES` - Time limit in minutes for responding to invitations (default: 5 minutes);
- `COMMUNITY_JOIN_COST_MANOR` - Join cost for manor communities paid by the community when invitation is approved (default: 1,500.00);
- `COMMUNITY_JOIN_COST_REALM` - Join cost for realm communities paid by the community when invitation is approved (default: 500.00);
- Timeout enforcement is automatic through the pending operations system, which checks every `PENDING_CHECK_INTERVAL_SECONDS`.

**Permission System:**
- Invitation privileges are governed by the member's role (not `APPLICANT` or `REFUSED`);
- No separate permission toggle exists—all eligible members may invite;
- Invitations must be sent to online players only.

**Commands:**
- `/community accept_invitation <communityIdentifier>` - Accept a pending invitation to join a community;
- `/community reject_invitation <communityIdentifier>` - Reject a pending invitation to join a community.

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
- **Teleport to Community** - Instant teleportation to the community's main location;
- **Teleportation Scope** - Select specific teleportation destinations within the community;
- **Community Chat** *()* - Access community messaging system;
- **Advancement** *()* - View community achievements;
- **Donate to Community** - Contribute currency to the community (opens the Assets menu);
- **Community Shop** *()* - Access the community marketplace;
- **Like Community** *()* - Rate the community;
- **Leave Community** - Exit the community; and
- **Invite Member** - Recruit new members to join by sending invitations to online players.

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

Communities maintain a treasury that tracks all member donations. Any player who is a member of a community (excluding those with `APPLICANT` or `REFUSED` status) may donate currency to support the community.

#### Donation Process

Members may donate through the **Community Assets Menu**, accessible via:

- The **Assets** button in the Community Menu (description section); or
- The **Donate to Community** button in the Community Menu (interaction section); or
- The **Assets** button in the Community Administration Menu (for administrators).

The donation menu presents six predefined amounts: **1.00**, **5.00**, **10.00**, **50.00**, **100.00**, and **500.00** (displayed as currency values divided by 100). When a member selects an amount:

- The system verifies the member has sufficient currency;
- The currency is deducted from the member's account;
- A `Turnover` record is created containing:
  - `amount` - The donation amount (integer);
  - `timestamp` - The time of donation (long); and
- The turnover is added to the member's `turnover` list in their member account.

#### Asset Inquiry

The **Community Assets Menu** displays:

- **Total Assets** - The net assets calculated as total donations minus total expenditures (such as invitation join costs), displayed prominently as a gold block with formatted currency value;
- **Donate** button - Opens the donation menu for contributing currency; and
- **Donor List** button - Opens the comprehensive donor list.

#### Donor List

The **Donor List Menu** presents all members who have made donations, sorted by total contribution in descending order. The list displays:

- Each donor represented by their player head;
- Total donation amount displayed beneath each donor's name; and
- Pagination supporting 45 donors per page.

Selecting a donor opens the **Donor Details Menu**, which shows:

- The donor's profile with total donation amount; and
- Up to 14 individual turnover records (most recent first), each displaying:
  - Donation amount;
  - Timestamp formatted as readable date and time.

#### Technical Implementation

Each member account maintains a `turnover` ArrayList that records all donation history. The community also maintains an `expenditures` ArrayList for tracking costs such as invitation-based membership fees. The community's total assets are calculated dynamically as: (sum of all donations) - (sum of all expenditures). The `getTotalDonation()` method aggregates each member's turnover records, while `getTotalAssets()` computes the net balance. The `getDonorList()` method returns member UUIDs sorted by total contribution, enabling efficient display of the most generous supporters.

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

Server operators possess elevated privileges for cross-community announcement management:

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

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.