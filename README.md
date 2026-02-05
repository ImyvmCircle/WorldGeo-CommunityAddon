# README

## Introduction

This Project is designed to constitute a comprehensive framework for players' **communities** in Minecraft servers. It is built on the IMYVMWorldGeo as an extension, which provides mechanics centered around **Region**, performing a Minecraft geography element, and is intended to offer an administration simulation system granting players the ability to self-govern their in-game regions in the form of a player community.

## Features

### Community

A **community** is a player organization linked to a valid and exclusive region in the IMYVMWorldGeo. A community organization is composed of members, a join policy and a status. For certain community, a council may be enabled.

#### Types

Players can establish two types of communities, manors (small-scale) or realms (large-scale). Realms have higher entry requirements but offer a **substantially higher ceiling**. Unlike manors, where expenses **surge** over time, realms benefit from decreasing marginal development costs as they grow.



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

#### Automatic Inspection and Proto-Communtiy

Once the request is sent, it will undergo the automatic inspections certificating conditions mentioned in order. Violations of these conditions may be reported by a message sending to the player. And if the request passed the inspections,  the player executing the process is charged, and a **proto-community** is created. Whether the proto-community becomes a **pending community** immediately is also decide by the community type. Whereas a realm stays in the recruiting status until it reaches the mininum requirement of realm population, which is, by default, 4 players, a manor becomes a **pending manor** directly. And a realm needs to recruit sufficient player in 48 hours(in reality) after executing the community request initialization, or it will become a revoked community, and the creation fee will be refunded, Once a realm reaches the population requirement, it also becomes a **pending realm**.

Server operators possess the permission **auditing the proto-communities**. If not passes, a community is revoked. Conversly, it becomes an **active community**.


## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.