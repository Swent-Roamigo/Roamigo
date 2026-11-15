# Trip invitations

AI assistance was used for implementation support.

An organiser invites people to a trip with a link and a code. Anyone who has either can join the
trip until the organiser stops the link or it expires. This page describes the flow, the data, the
security model, how to deploy the backend, and how to test it with two emulators.

## The flow

1. **Create.** The organiser taps Invite on the main trip card of Trips home, on the Trip Ready
   screen, or on the Members card of a trip's Plan tab. The app calls `createTripInvitation` and
   shows the invite sheet with the code. Asking again returns the same link while it is valid.
2. **Share.** "Share link" opens Android's share sheet with the text
   `Join my trip on Roamigo: roamigo://invite/<code>` and the code on a second line. "Copy code"
   puts the code on the clipboard. Nothing is sent by Roamigo itself.
3. **Open.** The other person either taps the link, which opens Roamigo through the `roamigo://invite`
   intent filter of `MainActivity`, or taps "Join" on Trips home and pastes the link, the whole
   message, or the bare code. The app calls `previewTripInvitation` and shows who invited them to
   which trip.
4. **Join.** "Join trip" calls `acceptTripInvitation`, which adds them to the trip. The app then
   opens the trip.
5. **Stop.** "Stop sharing this link" asks for confirmation and calls `revokeTripInvitations`. People
   who already joined stay on the trip.

A link opened while signed out is kept, and followed after the user has signed in. A link opened while
the app is already running reaches `MainActivity` as a new intent (it is `singleTop`), which
`RoamigoApp` passes to the navigation controller.

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

- Unit and Compose tests (Robolectric): `./gradlew testDebugUnitTest`. They cover link parsing, the
  service, both ViewModels, the invite sheet, the join screen and dialog, and the navigation, with
  `FakeTripInvitationService`.
- Functions on the emulators: `npm --prefix functions run test:emulator`. It runs real calls with
  Auth emulator tokens: permissions, expiry, revoking, joining twice, the member limit and the
  profile creation.
- Rules: `TripRepositoryFirestoreEmulatorTest` checks that a non-member cannot join by editing
  `memberIds` or adding a member, and that clients cannot read or write `tripInvitations`.

## Trying it with two emulators

The app talks to the real Firebase project, so the invitation functions must be deployed first.

1. Use two **different images**, for example `Pixel_10a` and `Small_Phone`. Two copies of the same image
   (`-read-only`) start with the same Google account, because it lives in the image.
2. Add a different Google account on each (Settings, Passwords & accounts), and sign in to Roamigo.
3. On the first, create a trip and open the invite sheet. Note the code.
4. Open the link on the second:
   ```sh
   adb -s emulator-5556 shell am start -a android.intent.action.VIEW -d "roamigo://invite/<code>"
   ```
   Or tap "Join" on Trips home and paste it. The clipboard is not shared between emulators.
5. Tap "Join trip". Then open the trip's Members screen on the first emulator: the second person is
   listed by name.

If an emulator cannot reach Google, restart it with `-dns-server 8.8.8.8,1.1.1.1`. Closing a
`-read-only` emulator discards everything on it.

## Limits

- `roamigo://` is not a web address, and many chat apps do not make it tappable. The code covers that.
  A web link needs Firebase Hosting and an app-links file.
- The person needs Roamigo installed. There is no web page to fall back on.
- Inviting a friend from the Friends list, and notifications about invitations, are not part of this.
