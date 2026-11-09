# Trip creation guidelines

These instructions apply to trip-creation UI and presentation code in this directory.

- Treat mockup destinations, travelers, and dates as examples unless the product explicitly requires them as initial values. Keep sample data in previews and tests.
- Define and enforce date and traveler constraints consistently across selection, submission, and the relevant data boundary. Check inclusive day/night calculations, past dates, traveler bounds, and transitions between solo and group modes according to current product requirements.
- Use the project's agreed timezone policy when comparing or persisting dates; do not silently introduce a new policy. Cover month and year boundaries when relevant.
- Preserve the trip draft across steps and configuration changes. For flows expected to survive process recreation, use an appropriate Android state-restoration mechanism and test restoration separately from rotation.
- Keep the primary action reachable on short screens and in landscape. Expose accessible labels and disabled states, and reuse existing test tags and unfinished-action feedback.
- For an integrated flow, verify navigation, submission, success, failure, and retry through the appropriate repository. Do not claim an unfinished or deliberately disconnected step is integrated.
