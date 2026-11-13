// AI assistance was used for implementation support.
// Exercises the trip invitation functions on the Auth, Firestore and Functions emulators:
//   npm --prefix functions run test:emulator
import {createRequire} from "node:module";
const require = createRequire(import.meta.url);
const {initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const P = "demo-roamigo";
initializeApp({projectId: P});
const db = getFirestore();
async function user(email) {
  const r = await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=x`,
    {method: "POST", headers: {"content-type": "application/json"}, body: JSON.stringify({email, password: "secret12", returnSecureToken: true})});
  const j = await r.json();
  return {uid: j.localId, token: j.idToken};
}
async function call(name, u, data) {
  const r = await fetch(`http://127.0.0.1:5001/${P}/europe-west1/${name}`,
    {method: "POST", headers: {"content-type": "application/json", ...(u ? {authorization: `Bearer ${u.token}`} : {})}, body: JSON.stringify({data})});
  const j = await r.json();
  return {status: r.status, body: j};
}
let failures = 0;
function check(label, cond, extra) { console.log((cond ? "PASS " : "FAIL ") + label + (cond ? "" : " " + JSON.stringify(extra))); if (!cond) failures++; }

const owner = await user("owner@example.com"), guest = await user("guest@example.com"), other = await user("other@example.com");
await db.doc("trips/t1").set({name: "Lisbon", ownerId: owner.uid, memberIds: [owner.uid], travelerCount: 3});
await db.doc(`trips/t1/members/${owner.uid}`).set({userId: owner.uid, role: "OWNER", joinTime: Timestamp.now()});
await db.doc(`users/${owner.uid}`).set({displayName: "Olivia"});

let r = await call("createTripInvitation", null, {tripId: "t1"});
check("unauthenticated create is refused", r.status === 401 || r.body.error?.status === "UNAUTHENTICATED", r);
r = await call("createTripInvitation", guest, {tripId: "t1"});
check("non-owner cannot create", r.body.error?.status === "PERMISSION_DENIED", r);
r = await call("createTripInvitation", owner, {tripId: "t1"});
const token = r.body.result?.token;
check("owner creates a link", typeof token === "string" && token.length >= 16, r);
const again = await call("createTripInvitation", owner, {tripId: "t1"});
check("asking again returns the same link", again.body.result?.token === token, again);
r = await call("createTripInvitation", owner, {tripId: "missing"});
check("missing trip is not found", r.body.error?.status === "NOT_FOUND", r);

r = await call("previewTripInvitation", guest, {token});
check("preview shows trip and inviter", r.body.result?.tripName === "Lisbon" && r.body.result?.inviterName === "Olivia" && r.body.result?.alreadyMember === false, r);
r = await call("previewTripInvitation", guest, {token: "does-not-exist-123456"});
check("unknown token is not found", r.body.error?.status === "NOT_FOUND", r);

r = await call("acceptTripInvitation", guest, {token});
check("guest joins", r.body.result?.tripId === "t1", r);
let trip = (await db.doc("trips/t1").get()).data();
check("guest is in memberIds", trip.memberIds.includes(guest.uid), trip);
const m = (await db.doc(`trips/t1/members/${guest.uid}`).get()).data();
check("guest member doc is EDITOR", m?.role === "EDITOR", m);
r = await call("acceptTripInvitation", guest, {token});
trip = (await db.doc("trips/t1").get()).data();
check("joining twice changes nothing", r.body.result?.tripId === "t1" && trip.memberIds.filter((x) => x === guest.uid).length === 1, [r, trip]);
r = await call("previewTripInvitation", guest, {token});
check("member sees alreadyMember", r.body.result?.alreadyMember === true, r);

r = await call("revokeTripInvitations", guest, {tripId: "t1"});
check("a member cannot revoke", r.body.error?.status === "PERMISSION_DENIED", r);
r = await call("revokeTripInvitations", owner, {tripId: "t1"});
check("owner revokes", r.body.result?.revoked === 1, r);
r = await call("acceptTripInvitation", other, {token});
check("revoked link no longer works", r.body.error?.status === "NOT_FOUND", r);
trip = (await db.doc("trips/t1").get()).data();
check("revoked link added nobody", !trip.memberIds.includes(other.uid), trip);

const t2 = (await call("createTripInvitation", owner, {tripId: "t1"})).body.result.token;
check("a new link differs after revoking", t2 !== token, t2);
await db.doc(`tripInvitations/${t2}`).update({expirationTime: Timestamp.fromMillis(Date.now() - 1000)});
r = await call("acceptTripInvitation", other, {token: t2});
check("expired link is refused", r.body.error?.status === "NOT_FOUND", r);

const t3 = (await call("createTripInvitation", owner, {tripId: "t1"})).body.result.token;
await db.doc("trips/t1").update({memberIds: Array.from({length: 10}, (_, i) => `u${i}`)});
r = await call("acceptTripInvitation", other, {token: t3});
check("a full trip refuses", r.body.error?.status === "RESOURCE_EXHAUSTED", r);

console.log(failures === 0 ? "ALL PASSED" : `${failures} FAILED`);
process.exit(failures === 0 ? 0 : 1);
