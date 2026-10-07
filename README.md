# Roamigo

### Description
Many groups of friends struggle to make decisions and stay coordinated while travelling because plans change, people split up, and everyone has different preferences about what to do next. Roamigo helps groups plan and experience trips together through a shared itinerary, live voting, group location sharing, and an interactive trip map.
Roamigo is designed mainly for students and young adults travelling with friends.


### Multi-user Support
Users can create a trip and invite their friends. Invited members can edit the same itinerary, suggest places, and vote on activities. Live voting lets the group quickly decide what activity or location to visit next. Only invited members can access and modify the trip. A world map heatmap shows the countries and regions the group has explored.


### Sensor Use
Roamigo uses device sensors to provide location-aware and photo-sharing features. GPS is used to show the live location of group members on the shared trip map. GPS is used to associate photos with the location where they were taken. The camera is used to capture photos that are automatically shared with the group and placed on the trip map.

### Offline Mode
Users can access cached itineraries, planned activities, and saved points of interest without an internet connection. Changes and photos created offline are stored locally and synchronized when connectivity returns. Live features such as member locations and new updates from the group require an internet connection. 

### Split-app model
Roamigo uses Firebase Authentication for Google Sign-In. Cloud Firestore stores trips, itineraries, points of interest, group membership, votes, and trip history. Firebase Realtime Database stores information that changes frequently, such as live member locations and live voting results. Firebase Storage stores photos shared during a trip. Mapbox provides the interactive map used to display activities, points of interest, member locations andgeolocated photos.

## Design Mockups
The application's UI/UX mockups are available on [Figma](https://www.figma.com/design/MBdvemRiwWcgU1ELe5FVE6/Roamigo-%25E2%2580%2593-UX-Mockup--Copy-?node-id=0-1&p=f&t=ogY45RA5UUrANx5v-0).

## Wiki
Additional project documentation, development conventions, and team processes are available in the **Roamigo Wiki**.

- **[Wiki Home](https://github.com/Swent-Roamigo/Roamigo/wiki)**
- **[Creating branches and writing pull requests](https://github.com/Swent-Roamigo/Roamigo/wiki/Creating-branches-and-writing-pull-requests)**
- **[Decision & Roles rotation (SM and PO)](https://github.com/Swent-Roamigo/Roamigo/wiki/Decision-&-Roles-rotation-(SM-and-PO))**

