# Overview
Track my buds is an application that can help track your near and dear ones.

# Core Entities
Following are the core entities in the system:
- **User** : A user who can create and account and use the application. A user will have the following attributes:
    - name - Name of the user. Type is string.
    - username - Username assigned to the user. Can be alpha numeric.
    - phoneNumber - Phone number with international code. Type is string.
    - email - Email address.
    - avatar - Avatar image.
    - locationSharingEnabled - Boolean. True by default.
- **Bots** : As the name suggest this is a bot that can perform a few activites within a group. We are not going to implement this now. Will implement this later on. Will update attributes here when needed.
- **Group** : Group represents a collection of users and bots. 
    - name - Name of the group. Type is string.
    - avatar - Avatar image.
- **Location** : Location of a user. Stored in latitude and longitudes.
    - lat - Numeric Latitude value.
    - long - Numberic Longitude value.
    - timestamp - Timestamp when the location is recorded.

# Actions
## User
- Can create a new account and login into the app using a phone number or login using Google SSO.
- User can create a new group. This user is the owner of the group. When creating the group, user has to provide the name for the group. The user who creates the group is marked as group owner by default.
- Users in a group can view the last known location of all the users who are part of a group.
- Users should be able to fetch list of all the groups it is part of.
- User should be able to remove itself from a group.
- User can choose to enable or disable sharing of the location.

## Group Owners (Users with special authorisations)
- Owner of the group can invite other users to the group. The invited user has to accept the invite to be part of a group.
- Owner of the group can make other users owner of the group.
- Owner of the group can remove the owner status of an owner in the group.
- Owner of the group can remove a user from the group.
