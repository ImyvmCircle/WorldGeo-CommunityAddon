---
name: community-permission-reformation
description: 系统重整聚落成员权限、状态与菜单命令检查
---

We should reform the permission of community members systematically. Check all menus and conditions and commands to ensure that the permission must be corresponding with context below:

To certificate if the execution one player done in a certain community is valid, first check the community status, then check the identity of the player in the community.

## Community Status

Community Status is precondition when any member of the community wants to execute some operations in community.

A proto-community, namely when a community is `RECRUITING` or `PENDING`, only two things related with recruitment may be handled by anyone, namely change join policy and audit new members, and quit the community;

An `ACTIVE` community member with proper role or identity may execute full functions;

Nothing may be done if a community is in `REVOKE` status. 

## Community Role Types

A player who is not a member, or is a member with `CommunityRoleType` of `APPLICANT` or `REFUSED` cannot be an object of any execution, except for an `APPLICANT` may be audited.

### OWNER(Full Authority)

An owner is granted with full authority to access administration menus, and may especially(which may not been done by an admin or a council):

- Transfer the owner of the community to someone; and
- enable or disable the council;
- toggle the permissions of executing administration affairs.

Fot the council and permission toggling, we should implement it by figuring out the range of community management and creating new menus for owner to manage them.

But they may not:

- promote, demote and remove themselves.
- quit the community

### ADMINISTRATOR

An administrator is granted with limited authority to access administration menus and perform some executions.

They may not:

- Execute affairs disabled by the owner;
- Change the name of the community; and
- Promote and demote any player, remove an owner, administrator or a councillor;

### Council

Council perform executions only when a vote is passed, and they may not:

- Execute affairs disabled by the owner;
- Promote, demote or remove an owner, remove a councillor.

### Member

A member may only view information and donate. They may be designated with identities.

### Others

A player who is not a member, or is a member with `CommunityRoleType` of `APPLICANT` or `REFUSED` can do nothing with the community. 

If a non-member try to click the community in the community list, send a menu ask if they want to join, if yes, then they are applicants.

If an `APPLICANT` or `REFUSED` tries to do so, then return a page telling them they are an `APPLICANT` or `REFUSED` 

## Steps

1. There remain many naming of 'operation' or 'op' concerning community internal administration. Rename all classes, functions and lang file items to distinguish administrator(of community) and operators(of the Server) at first.
2. List existing executions of community administration, implement owner council and permission management;
3. Implement `Others` part.
4. Scan for existing permission checks, extract them to an independent module, and makes them corresponding with the requirements above;
5. Modify README.

## Remember

1. Use Translator.tr() and `i18n` system of the mod, instead of using `Text.literal`.
2. Use `CommunityDatabase` saving information.
