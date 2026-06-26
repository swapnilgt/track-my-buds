# Overview
Track my buds is an application that can help track your near and dear ones.

# Functional Requirements
## Core Entities
Following are the core entities in the system:
- **User** : A user who can create and account and use the application. A user will have the following attributes:
    - name - Name of the user. Type is string. Max length 20 characters.
    - username - Username assigned to the user. Can be alpha numeric.
    - phoneNumber - Phone number with international code. Type is string.
    - email - Email address.
    - avatar - Avatar image.
    - locationSharingEnabled - Boolean. True by default.
- **Bots** : As the name suggest this is a bot that can perform a few activites within a group. We are not going to implement this now. Will implement this later on. Will update attributes here when needed.
- **Group** : Group represents a collection of users and bots. 
    - name - Name of the group. Type is string. Max Length 30 characters.
    - avatar - Avatar image.
- **Location** : Location of a user. Stored in latitude and longitudes.
    - lat - Numeric Latitude value.
    - long - Numberic Longitude value.
    - timestamp - Timestamp when the location is recorded.

## Actions
### User
- Can create a new account and login into the app using a phone number or login using Google SSO.
    - When logging in via phone number, OTP should be used as a second factor for authentication.
    - User should be able to choose a unique username when creating an account. Unless a username is chosen, the account is not deemed to be activated.
    - User should be able to update account fields other than the username.
    - When updating the phone number, use user should be asked to authenticate it via an OTP.
- User can create a new group. This user is the owner of the group. When creating the group, user has to provide the name for the group. The user who creates the group is marked as group owner by default.
- Users in a group can view the last known location of all the users who are part of a group.
- Users should be able to fetch list of all the groups it is part of.
- User should be able to remove itself from a group.
- User can choose to enable or disable sharing of the location.

### Group Owners (Users with special authorisations)
- Owner of the group can invite other users to the group. The invited user has to accept the invite to be part of a group.
- Owner of the group can make other users owner of the group.
- Owner of the group can remove the owner status of an owner in the group.
- Owner of the group can remove a user from the group.
- Owner of a group should be able to update the name and avatar of the group.

### Notifications
- When a user is invited to a group, a notification via email and SMS has to be sent to the invited user.
- When a user is made or removed as a group owner, a notification via email and SMS to be sent to the target user.

# Non-Functional Requirements
- Login and Signup should be highly available.
- Updating of the user profile details should be highly available.
- Updating of user profile details should be eventually consistent with an accepted delay of 5 minutes. The exception can be taken only for location sharing enablement/disablement. This we would want to be highly consistent as well.
- Creating of group should be highly available.
- Inviting a user to the group should be highly available.
- Accepting of invite to a group should be highly available.
- Removing a user from a group should be highly available and highly consistent.
- All notifications can expect a max delay of 5 minutes.
- Update for a location for a user should be highly available. Can expect a max delay of 30 seconds in it being eventually consistent.
- Promoting a user to a group owner should be highly available. There can be max delay of 5 minutes in eventual consistency.
- Demoting a user to a group should be highly available and highly consistent.
- Updating name and avatar of a group is highly available and eventually consistent with a max delay of 5 minutes.
- User removing itself from a group is highly available and eventually consistent with a max delay of 5 minutes.
- Fetching of list of all the groups the user is part of is highly available.
- Reading of live location of users in a group is highly available with an accepted delay of max 5 minutes.


# Requirements
- We want to build a user facing app from where user can access the different functionalities mentioned above.
- We want to build backend systems supporting the requirements mentioned above.


# Security
- User is able to access any of the APIs only when the account is in activated state.