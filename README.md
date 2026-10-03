# Roamigo

Roamigo is a travel organization and traveler meetup app designed primarily for young, budget-conscious travelers.

Planning and managing a trip often requires switching between multiple applications to organize tickets, reservations, activities, maps, schedules, and expenses. At the same time, solo travelers and small groups may want to meet other travelers nearby but lack an easy way to discover people following similar routes or visiting the same places.

Roamigo brings these features together into a single application.

## Features

### Authentication & Account Management
Users can securely access Roamigo using their Google account and manage their session across devices.

### Travel Wallet
The travel wallet centralizes important travel information such as tickets, reservations, hotel details, and bookings. Users can scan or photograph tickets, link them to itinerary items, manage what travel information is shared with others, and access essential documents offline.

### Itinerary
Users can organize their trip day by day by adding activities, points of interest, bookings, and tickets. The itinerary can be edited, reordered, viewed in a calendar-style format, and accessed offline.

### Map & Location
Roamigo displays trip locations and points of interest on a map alongside the user's current position. It also supports opening destinations in Google Maps, generating routes between itinerary stops, and detecting when the user is close to a saved location.

### Trip Sharing & Collaboration
Trips can be shared with other users, allowing groups to collaboratively manage the same itinerary. Trip owners can control permissions, manage members, and keep shared changes synchronized.

### Traveler Matching & Meetups
Users can discover nearby travelers with similar interests, destinations, or itineraries. Matching is optional, and users can control which travel information is visible to potential matches.

### Notifications & Context-Aware Features
Roamigo can send notifications based on the user's location and travel context, such as when a relevant itinerary location or compatible traveler is nearby. Notifications can also provide quick access to related bookings or itinerary information.

### Expenses
Users can track trip expenses, split costs between travelers, record who paid, and calculate balances between group members. Shared expense information is synchronized and can remain available offline.

### Offline Mode & Synchronization
Essential travel data such as itineraries, bookings, tickets, hotel information, and expenses is cached locally so that it remains accessible without an internet connection. Data can be synchronized again once connectivity is restored.

## Architecture

Roamigo follows a split-app model.

Firebase is used to store and synchronize:

- User profiles
- Trips
- Itineraries
- Bookings
- Tickets
- Expenses
- Shared trip information
- Traveler-matching data

Essential travel information is also cached locally on the device to support offline usage.

## Device Features
Roamigo makes use of several device capabilities:

- **GPS** : used for maps, location-aware travel information, and nearby traveler matching.
- **Camera** : used to capture ticket , QR codes and reservation photos.

## Target Audience
Roamigo is primarily designed for:
- Young travelers
- Students
- Solo travelers
- Small travel groups
- Budget-conscious travelers

## Mockup

The UI/UX mockup for Roamigo is available on [Figma](https://www.figma.com/design/V5BFsU4bmznAOxVv2L6uEK/Roamigo?node-id=0-1&t=dYkyiMs65qtDY8K7-1).
