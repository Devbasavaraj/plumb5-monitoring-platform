# Plumb5 engagement features: what changed and how to use it

Nothing here was compiled or run: the build sandbox could not reach Maven or
Google repositories, so there was no Android SDK, Spring or Firebase jars. All
files pass a syntax-only check. Build the SDK and the backend once and fix any
compile message before testing (details at the end).

## 1. How to apply

SDK (`monitoring-sdk` module): copy the contents of `sdk/plumb5/monitor/` over
your `com/plumb5/monitor/` source folder (23 files: 12 modified, 11 new in the
new `engage/` package). Replace the module's `AndroidManifest.xml` with
`sdk/AndroidManifest.xml` (adds POST_NOTIFICATIONS and one receiver). The
exact edits to existing files are in `modified-existing-files.patch`.

No new Gradle dependencies and no Room schema change are needed.

Backend: copy `backend/src/` into the license project. Three new tables
(`device_tokens`, `inapp_messages`, `chat_messages`) are created automatically
by `ddl-auto=update`; existing tables are not touched.

Also delete the `Plumb5Monitor.java.bak*/.backup*/.before*/.location-backup`
files from the SDK source folder (they are not compiled, but they are clutter).

## 2. Existing bugs fixed (small, targeted edits)

| Bug | Fix |
|---|---|
| `P5DialogBox` JS `dismiss()` called itself (stack overflow) | Dismisses on the UI thread |
| Beacon filters never matched: SDK saved plain UUID strings, matcher wanted objects; server's `beaconFilters` was never read | Matcher accepts both; sync prefers `beaconFilters` |
| Geofence/boot receivers could crash in a fresh process (`getInstance()` throws if `p5Init` never ran) | `P5Engage.ensureInitialized` rebuilds the SDK from saved config |
| All push notifications used request code 0 + `FLAG_ONE_SHOT`, so a later notification could open with an earlier one's extras | Unique request code per notification |
| `consumer-rules.pro` kept `com.example.monitoring_sdk` (gone), so R8 could rename Gson models | Points at `com.plumb5.monitor` |
| The push service in the manifest had no `onNewToken`, so token refreshes were never seen (`P5FireBaseManager` is not in the manifest) | `onNewToken` added to the declared service |

Left alone on purpose: the hard-coded `ADD_TO_CART` "PLUMB5 10% off" demo in
`LocalCampaignManager`. It can never appear (it needs an Activity but holds the
application context). Fixing it would suddenly show a demo offer in real apps.
Delete it when you are ready. The calculator-specific methods in
`Plumb5Monitor` are also untouched.

## 3. New features

**Push** (needs Firebase set up in the host app: `google-services` plugin + JSON)
- `P5Engine.p5EnablePush(activity)`: asks for POST_NOTIFICATIONS (Android 13+) and sends the FCM token to `POST /api/push/register`. Call it after login so the token is linked to the user's email.
- Token is re-sent on rotation, on user change, and every 7 days.
- Notification styles through the push data field `style`: `bigtext`, `inbox`, `multiline` (rows in `lines`, separated by `|`). No `style` = the previous behaviour.
- Tap tracking: event `PUSH_CAMPAIGN_CLICKED`. If your launcher activity is singleTop, also call `P5Engine.p5TrackPushClick(intent)` in `onNewIntent`.
- Backend: `POST /api/push/send` sends real FCM messages with firebase-admin (the old `adb` endpoint is untouched).

**In-app messages, forms, banner**
- Server-defined, shown natively (no WebView). Field types: text, image, input, checkbox, radio, dropdown, rating, countdown, button.
- Button actions: Screen, Deeplink, Browser, Copy, Call (opens the dialer, no CALL_PHONE permission), Share, Sms, Reminder, Event, Permission, Close.
- A message fires when an SDK event with its `trigger` name is recorded (`APP_FOREGROUND`, `GEOFENCE_ENTER`, `BEACON_DETECTED`, custom events...), optionally filtered by one event property. Frequency caps: `maxImpressions`, `minIntervalHours`.
- `P5Engine.p5ShowInApp(activity, "key")` shows the message whose trigger is `MANUAL:key` (replaces the old static forms).
- `P5Engine.p5LoadBanner(activity, bannerImageView, closeImageView)`.
- Events recorded: `INAPP_VIEW`, `INAPP_CLICK`, `INAPP_CLOSE`, `INAPP_FORM_SUBMIT` (answers in the payload).
- `P5Engine.p5ScheduleReminder(context, minutes, text)` plus the Reminder action. Reminders do not survive a reboot.

**Location triggers**
- The geofence list from the config sync is saved and registered again at app start and after reboot (before, only the 15-minute sync registered them, and Play services forgets geofences on reboot).
- Geofence and beacon events feed the same in-app trigger system, so an offer can open on `GEOFENCE_ENTER` (with `triggerProp: "geofence_id"`). When the app is in the background, a message whose content has `notificationBody` becomes a local notification instead.

**Chat**
- Backend serves the page the SDK already opens (`/chat/{appId}`), with polling every 3 seconds. Agents answer through `/api/chat/reply`. The SDK change is one line (WebView DOM storage, so the visitor keeps their conversation).

## 4. Backend setup

Add to `application.properties` (already appended in the provided file):
```
plumb5.admin.token=<a long random secret>
firebase.credentials.path=/path/to/firebase-service-account.json
```
Admin actions (send push, create/toggle/delete in-app messages, chat replies)
need the header `X-Admin-Token`. The license API key is compiled into every
app copy, so it must not be enough to push to all users. With the token left
empty, those endpoints answer 403 and nothing else changes.

```
# Send a push to everyone, with a big-text style
curl -X POST http://localhost:9090/api/push/send \
  -H "X-Monitoring-Api-Key: P5-TEST-KEY-12345" -H "X-Admin-Token: <secret>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Sale","message":"50% off today","style":"bigtext","target":{"type":"all"}}'

# Offer shown when the user enters geofence "1"
curl -X POST http://localhost:9090/api/inapp/create \
  -H "X-Monitoring-Api-Key: P5-TEST-KEY-12345" -H "X-Admin-Token: <secret>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Store offer","type":"dialog","trigger":"GEOFENCE_ENTER",
       "triggerProp":"geofence_id","triggerValue":"1","maxImpressions":3,"minIntervalHours":24,
       "content":{"formName":"store_offer","fields":[
         {"type":"text","text":"Welcome in!","size":22,"bold":true,"align":"center"},
         {"type":"input","key":"email","hint":"Your email","inputType":"email","required":true},
         {"type":"button","text":"Get offer","submit":true,"action":{"type":"Browser","value":"https://example.com/offer"}}],
        "notificationTitle":"Store offer","notificationBody":"Open the app for your welcome offer"}}'
```

## 5. Things you must check

1. Sample app server URL: `Plumb5Monitor.init(..., "http://10.0.2.2:9090/api/events")` makes every request go to `/api/events/api/...`. Use the base URL only: `http://10.0.2.2:9090`.
2. Android 9+ blocks plain `http://` unless the host app allows cleartext (add `android:usesCleartextTraffic="true"` for the emulator/dev build only).
3. Background geofence delivery on Android 10+ needs ACCESS_BACKGROUND_LOCATION in the host app. I did not add it to the SDK manifest, because it forces a Play Store declaration on every app that uses the SDK.
4. The push flow needs your `google-services.json` and the Firebase plugin in the app module; the sample app has neither.
5. Geofences removed on the server are not unregistered on the device yet (the controller has no remove call).
6. Chat is anonymous (random session id kept in the WebView) and keyed by app id only; there is no image upload or unread badge yet. Add rate limiting before exposing it to the internet.
7. The old Plumb5 GCM mode and the Estimote SDK are intentionally not ported (FCM and your native BLE scanner replace them).

## 6. Suggested test order

1. Build both projects; fix any compile messages.
2. Register: call `p5EnablePush` and check a row in `device_tokens`.
3. Send a push with `/api/push/send`; tap it and look for `PUSH_CAMPAIGN_CLICKED` in `event_records`.
4. Create a dialog with trigger `APP_FOREGROUND`; reopen the app (the SDK refreshes at most every 15 minutes; call `p5RefreshCampaigns` to force it).
5. Add a geofence through `/api/config/geofence/add`; reboot the device and confirm the geofence still fires.
6. Open the chat from `Plumb5Monitor.getInstance().showChat(activity)`; reply through `/api/chat/reply`.
