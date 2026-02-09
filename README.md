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

`RECRUITING` is a unique status to realms, during which a realm may gather sufficient members to enter into the next stage, or it will be revoked. At this stage, only the coomunity name, join policy and the membership be adminstrated. Member who joined the community may also leave it.

`PENDING` is a status where the community request meets the automatic requirements and waits for a Server operator to audit it. The administrative power of a pending community is the same as that of a recruiting community.

In `ACTIVE` status, all available administration power of a community is unlocked, and may be exercised freely. A council may be called by the owner to act as an agent.

If a community is in `REVOKED` status, however, its owner and members is restricted from all actions, including leave it. Server operators may adopt an approach to handle it in time, whether making it an eligible community again or delete it.

Technically, types and status are combined as one parameter of a community.

### Community Creation

To create a community, a player who desires to be the founder and the owner may initialize a request, and if it passes checks, recruitment process and audition, an active community owned by the player will be added to the Minecraft Server.

#### Request Initialization

A **community creation request** may be initialized spontaneously by any player trying to inaugurate one, providing

- that the player is not at the time of application, **a member of any other community of the same *community type***, which may be choosen when initiating the creation request;

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

When criteria above are achieved, a player may **initializing the creation request**, and the player

- defines the `Community Name`, `Community Type` and `GeoShapeType` in this step;
- may use the command `/community create <geoShapeType> <communityType> [communityName]`; and
- may left-click the `Create Community` button in the box-interface `Community Main Menu`, set the community information in this step as mentioned, left-click `Confirm Creation` button, and then `Confirm` again.

#### Automatic Inspection and Proto-Community

Once the request is sent, it will undergo the automatic inspections certificating conditions mentioned in order. Violations of these conditions may be reported by a message sending to the player. And if the request passed the inspections,  the player executing the process is charged, and a **proto-community** is created. Whether the proto-community becomes a **pending community** immediately is also decide by the community type. Whereas a realm stays in the recruiting status until it reaches the mininum requirement of realm population, which is, by default, 4 players, a manor becomes a **pending manor** directly. And a realm needs to recruit sufficient player in 48 hours(in reality) after executing the community request initialization, or it will become a revoked community, and the creation fee will be refunded, Once a realm reaches the population requirement, it also becomes a **pending realm**.

Server operators possess the permission **auditing the proto-communities**. If not passes, a community is revoked. Conversly, it becomes an **active community**.

### Community Administration

A community's owner and administrators possess extensive management capabilities accessible through the **Community Operations** button in the community menu, which opens the **Community Administration Menu**:

- **Rename Community** - Modify the community's name through an anvil interface;
- **Manage Members** - Access comprehensive member management tools;
- **Community Audit** - Review and process membership applications when join policy requires approval;
- **Announcement** - Post announcements to all members;
- **Advancement** *()* - Manage community achievements and progression systems;
- **Assets** *()* - Administer community resources and treasury;
- **Region Geometry** - Modify the community's geographical boundaries and shape;
- **Region Settings** - Adjust properties and rules for the community's region;
- **Teleport Points** - Create and manage teleportation destinations within the region; and
- **Join Policy** - Toggle between `OPEN`, `APPLICATION`, or `INVITE_ONLY` policies, displayed in the menu as green, yellow, or red respectively.

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
- `governorship` - Custom governance value for advanced features; and
- `mail` - An internal notification system (ArrayList) for community communications.

#### Member Interaction *()* 

Members may interact with their community through the **Community Menu**, accessible from the **My Communities** list in the main menu:

- **Description** - View detailed information about the community's region;
- **Announcement** *()* - View community announcements;
- **Members** - Browse the complete member list;
- **Assets** *()* - View community resources;
- **Settings** - Adjust personal community preferences through the **Community Setting Menu**;
- **Teleport to Community** - Instant teleportation to the community's main location;
- **Teleportation Scope** - Select specific teleportation destinations within the community;
- **Community Chat** *()* - Access community messaging system;
- **Advancement** *()* - View community achievements;
- **Donate to Community** *()* - Contribute resources to the community;
- **Community Shop** *()* - Access the community marketplace;
- **Like Community** *()* - Rate the community;
- **Leave Community** *()* - Exit the community; and
- **Invite Member** *()* - Recruit new members to join.

### Council System

The **Council** is an independent governance system that may be enabled for a community to facilitate collective decision-making. When enabled, designated council members may participate in voting on significant community matters as an agent acting on behalf of the owner and administrators.

#### Council Structure

- Councils maintain an `enabled` flag indicating whether the system is active for the community;
- Only members with `isCouncilMember` set to `true` in their member account may participate in council functions; and
- Council membership is independent of the basic role hierarchy, allowing regular members to participate in governance.

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