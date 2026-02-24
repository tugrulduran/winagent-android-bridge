# 📲 WinAgent Bridge (Android)

**WinAgent Bridge** is a lightweight Android companion app that **forwards phone events** (📳 calls, ☎️ missed calls, 🔔 notifications) to **WinAgent** running on your Windows PC.

- 🖥️ **WinAgent (Windows host + plugin system):** https://github.com/tugrulduran/winagent
- 📡 **This app (Android Bridge):** pairs automatically on your local network and sends compact JSON packets when events happen.

> 🎯 Goal: keep the phone-side logic **tiny + low-power**, and let WinAgent handle **UI, storage, and analytics**.

---

## ✨ Features

- 🔎 **Auto pairing / discovery** (no manual IP typing)
- 🔔 **Notifications**
  - Snapshot of currently active notifications
  - Per-notification **posted/removed** updates
- 📞 **Calls**
  - **Phone ringing** event
  - **Missed calls** list (best-effort via Call Log)
- 🧪 **Trigger button** (manual “send now” for testing)
- 🪶 **Low overhead** (no constant scanning; reacts to OS events)

---

## ✅ Requirements

- 🤖 **Android 8.0+ (API 26+)**
- 🧰 **Android Studio** (JetBrains)
- 📶 Phone + PC on the **same LAN / Wi‑Fi**
- 🧩 WinAgent on the PC with a plugin that:
  - Responds to discovery on **UDP `45151`**
  - Receives events on **UDP `45152`** (or the port it advertises in the discovery response)

---

## ▶️ Run / Install (Android Studio)

1. Open the project folder in **Android Studio**
2. Plug your phone in (USB debugging enabled) 📱🔌  
   *(Real device recommended for calls/notifications.)*
3. Click **Run** ▶️

---

## 🧩 First-time setup

### 1) Pair with the PC (no IP typing) 🔍
1. Start **WinAgent** on your PC 🖥️
2. Open **WinAgent Bridge** on your phone 📲
3. Tap **“Scan”** to start discovery

The app broadcasts a discovery packet and waits for WinAgent to reply.

### 2) Enable required permissions 🔐

**Call Log**
- Tap **“Ask for Permission”** to grant:
  - `READ_PHONE_STATE` → ringing state
  - `READ_CALL_LOG` → missed call details

**Notifications**
- Tap **“Open Settings”** and enable **Notification Access** for *WinAgent Bridge* ✅

> ℹ️ Some OEMs (Xiaomi/OPPO/Huawei) may need extra “Auto-start / Battery optimization” exceptions for reliable background behavior.

---

## 🌐 Network protocol (UDP)

### 🔌 Ports
- 🔎 Discovery: **UDP `45151`**
- 📦 Events: **UDP `45152`** (default)

The PC can override the event port by returning a different `port` value in the discovery response.

---

## 🔎 Pairing / Discovery

### 📤 Phone → broadcast (UDP `45151`)
```json
{ "t":"discover_req", "v":1, "deviceId":"...", "deviceName":"..." }
```

### 📥 PC → unicast reply (to source IP:port)
```json
{ "t":"discover_res", "v":1, "ip":"<pc-ip>", "port":45152, "pcName":"...", "token":"optional" }
```

**Notes:**
- If `ip` is empty, the app falls back to the sender IP of the UDP response.
- If `token` is provided, the app includes it in every event packet ✅

---

## 📦 Event packets

All events use the same envelope:

```json
{
  "t": "event",
  "v": 1,
  "evt": "<event-name>",
  "ts": 1710000000000,
  "token": "optional",
  "data": { }
}
```

### 📳 `ringing`
Sent when the phone state becomes **RINGING** (if enabled):
```json
{ "t":"event", "evt":"ringing", "data": {} }
```

### ☎️ `missed_calls`
Sent when a call ends as **missed** (RINGING → IDLE without OFFHOOK), if enabled:
```json
{
  "evt": "missed_calls",
  "data": {
    "count": 2,
    "items": [
      { "number": "+90...", "name": "Alice", "dateMs": 1710..., "durationSec": 0, "isNew": true }
    ]
  }
}
```

### 🔔 `notifications_snapshot`
Sent once when the Notification Listener connects (if enabled):
```json
{
  "evt": "notifications_snapshot",
  "data": {
    "count": 3,
    "items": [
      { "package": "com.app", "key": "...", "id": 12, "postTime": 1710..., "title": "...", "text": "..." }
    ]
  }
}
```

### 🧾 `notification`
Sent on each notification **posted** or **removed**:
```json
{
  "evt": "notification",
  "data": {
    "action": "posted",
    "item": { "package": "com.app", "key": "...", "id": 12, "postTime": 1710..., "title": "...", "text": "..." }
  }
}
```

---

## 🧱 Project structure (where to extend)

- `MainActivity.kt` 🧩  
  Compose UI (Pairing + 2 tabs: Calls / Notifications)
- `SettingsRepo.kt` 💾  
  Settings stored in **DataStore** (toggles + paired PC info + device id)
- `UdpDiscovery.kt` / `UdpSender.kt` 📡  
  Discovery + UDP send
- `EventDispatcher.kt` 🚀  
  Central place that builds/sends event packets
- `PhoneStateReceiver.kt` 📞  
  Low-power call monitoring (OS wakes it on phone state changes)
- `WaNotificationListener.kt` 🔔  
  NotificationListenerService (snapshot + posted/removed)

---

## 🧯 Troubleshooting

### ❌ “Scan” can’t find anything
- Phone + PC must be on the **same Wi‑Fi/LAN** 📶
- Guest Wi‑Fi / AP isolation can block broadcast 🚫
- Check Windows Firewall rules for UDP `45151` / `45152` 🔥
- Verify the PC plugin replies with `discover_res`

### 📵 Missed calls are empty / inconsistent
- Ensure `READ_CALL_LOG` is granted ✅
- Call log behavior varies by OEM/ROM; “new missed call” filtering may differ

### 🔕 Notifications don’t arrive
- Ensure **Notification Access** is enabled ✅
- Some OEMs require extra battery/auto-start permissions ⚠️

---

## 🔗 Related project: WinAgent

**WinAgent** is an open-source modular Windows monitoring agent built with **Qt + C++**, designed around a plugin system and real-time telemetry.

👉 Repository: https://github.com/tugrulduran/winagent

---

## 📄 License

This bridge app is intended as a companion to WinAgent.  
If you plan to publish/distribute this repo separately, add a `LICENSE` file.
