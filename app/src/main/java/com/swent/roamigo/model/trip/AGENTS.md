# Trip data guidelines

These instructions apply to trip data and repository code in this directory.

- Enforce the trip invariants required by the current model and product rules at the data boundary; do not rely solely on UI validation. Cover invalid dates, destinations, traveler counts, and authorization when relevant.
- Use the project's agreed timezone policy for date conversion and persistence; do not silently introduce a new policy. Test relevant day, month, year, and timezone boundaries.
- Keep Firebase implementation details behind repository boundaries. Make dependencies injectable and use controlled fakes or isolated Firebase emulators in tests.
- For writes and retries, consider duplicate submissions and ambiguous failures; test the relevant behavior and document any unresolved limitations.
