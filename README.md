# WinAgent Bridge (Android)

Minimal Android companion app for your C++/Qt WinAgent toolchain.

## What it does (v0.1)
- **Calls tab**
  - "Anlık aramaları gönder" → updates `phoneRinging` and pushes a full state packet.
  - "Cevapsız aramaları gönder" → reads CallLog (NEW=1) and pushes a full state packet.
- **Notifications tab**
  - "Bildirimleri gönder" → using a Notification Listener, tracks active notifications and pushes a full state packet on posted/removed.

Everything is pushed to the paired PC over **TCP** (discovery stays UDP).

## Pairing / discovery
- Phone sends a UDP broadcast JSON packet:
  - port: `45151`
  - payload: `{ "t":"discover_req", "v":1, "deviceId":"...", "deviceName":"..." }`
- PC should respond (unicast back to source IP:port) with:
  - `{ "t":"discover_res", "v":1, "ip":"<pc-ip>", "port":45152, "pcName":"...", "token":"optional" }`

The app stores `ip/port` (and optional `token` for future use).

## Data packet (TCP to PC)
On *any* interrupt-like event (phone ringing change, missed call detected, notification posted/removed), the app sends **one full state packet**:
```json
{
  "notifications": [
    {"id":"<unique>","package":"...","postTime":123,"title":"...","text":"...","androidId":7}
  ],
  "missedCalls": [
    {"id":123,"number":"...","name":"...","dateMs":123,"durationSec":0,"isNew":true}
  ],
  "phoneRinging": true
}
```

"id" for notifications is **unique** and stable during the notification's lifetime (uses Android's `StatusBarNotification.key`).

There is also a **Trigger** button in the UI to force a refresh and send a packet (useful for testing).

## Power usage
- **No continuous scanning**.
- **No foreground service**.
- Calls are handled via a manifest `BroadcastReceiver` (system wakes the app only on call state changes).
- Notifications are handled via `NotificationListenerService` (system-driven).

For notifications, Android requires the user to enable Notification Listener access in Settings.

