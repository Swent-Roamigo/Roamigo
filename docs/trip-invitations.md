# Trip invitations

AI assistance was used for implementation support.

An organiser invites people to a trip with a link and a code. Anyone who has either can join the
trip until the organiser stops the link or it expires. This page describes the flow, the data, the
security model, and how to deploy and test the backend. The screens a person uses to open a link and
join are a separate change, and this page is extended there.

## The flow

1. **Create.** The organiser taps Invite on the main trip card of Trips home, on the Trip Ready
   screen, or on the Members card of a trip's Plan tab. The app calls `createTripInvitation` and
   shows the invite sheet with the code. Asking again returns the same link while it is valid.
2. **Share.** "Share link" opens Android's share sheet with the text
   `Join my trip on Roamigo: roamigo://invite/<code>` and the code on a second line. "Copy code"
   puts the code on the clipboard, marked as sensitive on Android 13 and later. Nothing is sent by
   Roamigo itself.
3. **Stop.** "Stop sharing this link" asks for confirmation and calls `revokeTripInvitations`. People
   who already joined stay on the trip. If stopping fails, for example offline, the link stays on
   screen with a message that it still works, so it is never mistaken for stopped.

Only the trip organiser can create or stop a link. Another member who opens the sheet is told so.

## Data

`tripInvitations/{token}` is written and read only by the functions. The token is the document ID:
16 random bytes in URL-safe base64.

| Field | Meaning |
|---|---|
| `uid`, `token` | The token |
| `tripId` | The trip it admits to |
| `inviterUserId` | The trip owner who created it |
| `inviteeUserId` | `null`: a link anyone can use |
| `status` | `PENDING`, `REVOKED` or `EXPIRED` |
| `creationTime`, `expirationTime` | Valid for 7 days |

Joining adds the user to `memberIds` and creates `trips/{tripId}/members/{userId}` with the role
`EDITOR`, in one transaction. That also triggers `syncTripMember`, which lets the new member share
their location. A trip that gains a second member becomes a `GROUP` trip, and `travelerCount` never
falls below the number of members, because the Firestore rules tie the two together.

## Security

- Clients cannot read or write `tripInvitations`, edit `memberIds`, or create another user's member
  document (`firestore.rules`). Joining is only possible through the functions, which write as Admin.
- An unknown, revoked or expired code gets the same answer, `not-found`, so codes cannot be probed.
- Only the trip owner creates or revokes links. A link stops working if the person who made it is no
  longer the owner.
- A trip accepts at most 10 members.
- Anyone who has the code can join until it is stopped or expires. Treat it like a key.

The functions also create the caller's `users/{uid}` profile when it is missing, from their Firebase
account. The app saves the profile itself after sign-in, but only logs a warning when that fails, and
other members then see the person as an unknown traveller. An existing profile is never changed, and
an account without a name gets none.

## Deploy

The four functions are in `functions/src/tripInvitations.ts`, in `europe-west1`. They must be public
(`allUsers` as invoker), because the Firebase client calls them before the function can check the
sign-in itself. Deploying a function for the first time sets that, which needs the Cloud Functions Admin
and Cloud Run Admin roles (Owner has both). Later updates keep it.

```sh
firebase deploy --only functions:createTripInvitation,functions:previewTripInvitation,functions:acceptTripInvitation,functions:revokeTripInvitations --project roamigo-c6cb2
```

If the deploy says `Failed to set the IAM Policy`, the Firebase CLI account lacks that role, and the
app will get HTTP 403 from every call. Check with `firebase login:list`, grant the role, delete the
four functions with `firebase functions:delete`, and deploy them again, because an update does not
retry the setting. A request without a sign-in must answer `401 UNAUTHENTICATED`, not 403.

## Tests

- Unit and Compose tests (Robolectric): `./gradlew testDebugUnitTest`. They cover the service, the
  invite sheet and its ViewModel, and the entry points, with `FakeTripInvitationService`.
- Functions on the emulators: `npm --prefix functions run test:emulator`. It runs real calls with
  Auth emulator tokens: permissions, expiry, revoking, joining twice, the member limit and the
  profile creation.
- Rules: `TripRepositoryFirestoreEmulatorTest` checks that a non-member cannot join by editing
  `memberIds` or adding a member, and that clients cannot read or write `tripInvitations`.

## Limits

- `roamigo://` is not a web address, and many chat apps do not make it tappable. A web link needs
  Firebase Hosting and an app-links file.
- Opening a link or entering a code in the app comes with the screens for joining.
- Inviting a friend from the Friends list, and notifications about invitations, are not part of this.
