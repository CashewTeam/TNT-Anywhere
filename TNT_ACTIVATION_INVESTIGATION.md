# TNT Activation Investigation

## Purpose

This note records the current reverse-engineering progress around how SmartisanOS TNT is actually activated, especially for the question:

- can `app-mirror` trigger a real TNT desktop session without physical TNT hardware?
- is writing `global_pc_mode_settings` enough?
- what role do the `100000+` displays play?

The findings below are based on:

- `smartisanos/smartisan-framework-tnt`
- `smartisanos/smartisan-services-tnt`

## Current High-Level Conclusion

At this stage, the evidence strongly suggests:

1. `global_pc_mode_settings` is mainly a state output written by system services after TNT entry, not the real entry trigger.
2. A real TNT session is orchestrated by system services, mainly `TntManagerService`, not by an ordinary app directly.
3. SmartisanOS creates a dedicated TNT virtual display named `smt.tnt.virtual.display`, and remaps it to display IDs starting at `100000`.
4. TNT entry logic reacts to the lifecycle of that `100000+` virtual display, not directly to a normal external display.
5. The TNT virtual display appears to sit on top of a lower-level "base external display" that the system must first accept as valid.
6. Therefore, simply creating an app-side virtual display or toggling a global setting is unlikely to reproduce the full official TNT activation path.

## Key Findings

### 1. `TntManagerService` is the real TNT mode controller

File:

- [smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

Important points:

- `enterPCModeLocked(int displayId)` performs the real PC-mode enter sequence:
  - power / observer setup
  - wakeup
  - service binding
  - `mTntService.enterPcMode(displayId)`
  - then writes `global_pc_mode_settings = 1`
- `exitPCModeLocked(...)` performs the reverse and writes `global_pc_mode_settings = 0`

Relevant references:

- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)
- [TntManagerService.java:1268](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1268)
- [TntManagerService.java:1315](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1315)

Interpretation:

- `global_pc_mode_settings` looks like a result flag written by `TntManagerService`, not the root cause that makes TNT start.

### 2. The system only treats `100000+` displays as TNT entry candidates

Still in:

- [TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

Important points:

- `scheduleDisplayAdded(...)` explicitly ignores display IDs below `100000`
- `handleDisplayAdded(...)` is the actual branch that may enter TNT
- `scheduleDisplayRemoved(...)` also ignores non-virtual display IDs

Relevant references:

- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1416](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1416)
- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntManagerService.java:1466](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1466)

Interpretation:

- The system does not directly enter TNT on a raw lower-ID external display.
- Instead, it waits for the TNT-layer virtual display in the `100000+` range.

### 3. `100000+` is the dedicated TNT virtual display space

File:

- [smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java)

Important points:

- `mNextTntVirtualDisplayId = 100000`
- the system creates a virtual display named `smt.tnt.virtual.display`
- `assignTntVirtualDisplayIdIfNeeded(...)` remaps that display into the `100000+` range

Relevant references:

- [TntDisplayManagerServiceImpl.java:47](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:47)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:220](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:220)
- [TntDisplayManagerServiceImpl.java:290](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:290)

Interpretation:

- The `10000x` / `100000+` display IDs observed during debugging are expected and intentional.

### 4. TNT virtual display is layered on top of a base display

Still in:

- [TntDisplayManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java)

Important points:

- `addVirtualDisplayLocked(...)` first validates a base display with `SmtPCUtilsInner.isValidExtDisplayType(...)`
- it records that display as `mBaseDisplayId`
- then creates `smt.tnt.virtual.display`
- `showVirtualDisplayIfNeededLocked(...)` decides whether to return default display, base display, or TNT virtual display

Relevant references:

- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:208](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:208)
- [TntDisplayManagerServiceImpl.java:226](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:226)
- [TntDisplayManagerServiceImpl.java:265](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:265)

Interpretation:

- The architecture appears to be:
  - first a valid base external display/session exists
  - then system creates TNT virtual display on top
  - then `TntManagerService` enters TNT based on that TNT virtual display

This matches the observed logs:

- lower display ID: base display/session
- `100000+`: TNT virtual display

### 5. Real TNT entry also updates AMS/WMS state

File:

- [smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java)

Important points:

- `enterPcMode(int displayId)` calls:
  - `SmtPCUtilsInner.setIsPcMode(displayId, true)`
  - `mTntWindowManager.enterPcMode(display, true)`

Relevant references:

- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntActivityTaskManagerServiceImpl.java:519](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:519)

Interpretation:

- A real TNT session is not just "a display exists".
- It also requires system window/activity policy to flip into PC mode for that display.

### 6. The framework side also assumes TNT is already system-established

File:

- [smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java)

Important points:

- `global_pc_mode_settings` is observed by framework code
- for non-system apps, `adjustChoosePresentationDisplay(...)` filters out displays with `displayId >= 100000` unless PC mode is already on

Relevant references:

- [TntMediaRouterImpl.java:26](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java:26)
- [TntMediaRouterImpl.java:54](E:/Sunshine-android-master/smartisanos/smartisan-framework-tnt/sources/android/media/TntMediaRouterImpl.java:54)

Interpretation:

- Even framework presentation behavior assumes `100000+` TNT displays are special and should be hidden from ordinary app flow until TNT is active.

### 7. `tnt_display_connected` appears to be hardware-oriented

Back in:

- [TntManagerService.java](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java)

Important points:

- `tnt_display_connected = 1` is written when TNT USB-related hardware is attached
- `tnt_display_connected = 0` is written on disconnect

Relevant references:

- [TntManagerService.java:3196](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3196)
- [TntManagerService.java:3358](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3358)

Interpretation:

- This setting looks related to official TNT hardware detection, not a general-purpose TNT activation switch.

### 8. There is a dedicated "Boston / TNT Anywhere" special path

Relevant references:

- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [TntPowerManagerServiceImpl.java:59](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/power/TntPowerManagerServiceImpl.java:59)

Important points:

- system code checks `SmtPCUtilsInner.isDisplayForTntAnywhere(ownerPackageName)`
- there are explicit checks for `com.smartisanos.boston.phone`

Interpretation:

- Smartisan likely has a privileged internal display/session path for wireless TNT / Boston hardware/app flow.
- That path is different from how a normal third-party app-created display would appear.

## What This Means For `app-mirror`

Based on the current evidence, `app-mirror` can likely:

- create capture / stream / control pipelines
- create or work with app-side displays
- possibly trigger TNT-like UI rendering on certain displays

But `app-mirror` is unlikely to fully reproduce official TNT activation unless it can also satisfy the system-service side expectations, such as:

- a valid base external display classification
- TNT virtual display creation through system display service logic
- `TntManagerService` / AMS / WMS PC-mode orchestration
- possibly Boston / TNT Anywhere-specific conditions

## New Findings From `framework.jar`

We have now located the real framework-side implementations:

- [SmtPCUtilsInner.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java)
- [SmtPCUtils.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java)
- [SmtPCUtilsSmtBase.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java)
- [SmtPCUtilsInnerBase.java](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java)

This confirms that `SmtPCUtilsInner` is not just referenced by services; it is a real Smartisan framework rule center.

### 9. `SmtPCUtilsInner` is a rule center, not just a thin helper

Important points:

- `SmtPCUtilsInner` stores `sDisplayIdInPcMode`
- `setIsPcMode(displayId, true/false)` only updates that framework-side state
- `isPcMode()` in `SmtPCUtilsInnerBase` checks whether `sDisplayIdInPcMode` is a valid external display id

Relevant references:

- [SmtPCUtilsInner.java:278](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:278)
- [SmtPCUtilsInnerBase.java:13](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java:13)
- [SmtPCUtilsInnerBase.java:18](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInnerBase.java:18)

Interpretation:

- Framework-side "pc mode working" is driven by a display-id-based state model.
- The real display id in PC mode is a core piece of system state.

### 10. `SmtPCUtils` framework code binds to the TNT system service `smt_pcm`

Important points:

- `SmtPCUtilsSmtBase.getSmtPCManager()` uses `ServiceManager.getService("smt_pcm")`
- it wraps that binder as `android.pc.ISmtPCManager`

Relevant references:

- [SmtPCUtilsSmtBase.java:39](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:39)
- [SmtPCUtilsSmtBase.java:44](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:44)

Interpretation:

- App/framework-side `SmtPCUtils*` APIs are a client facade over the `smt_pcm` system service.
- Earlier `SystemServer` findings and framework findings now line up cleanly.

### 11. `ISmtPCManager` is the binder contract implemented by `TntManagerService`

Important points:

- `TntManagerService` extends `ISmtPCManager.Stub`
- `ISmtPCManager` includes methods such as:
  - `getCurrentExtDisplayId()`
  - `isTntDisplay()`

Relevant references:

- [TntManagerService.java:130](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:130)
- [ISmtPCManager.java:48](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/pc/ISmtPCManager.java:48)
- [ISmtPCManager.java:112](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/pc/ISmtPCManager.java:112)

Interpretation:

- `SmtPCUtils -> ISmtPCManager -> TntManagerService` is now a confirmed call path.

### 12. Framework external-display validation rules are now known

Important points:

- `isValidExtDisplayType(int type, String pkgName)` accepts:
  - `type == 2`
  - `type == 3`
  - `type == 5` only if package is in Smartisan PC-mode allowlist
  - `type == 4` only when overlay-display test property is enabled

Relevant references:

- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)

Interpretation:

- This is a major breakthrough for understanding why some displays are accepted and some are ignored.
- A third-party-created display must match Smartisan's accepted type/package model to participate in TNT logic.

### 13. The Smartisan PC-mode package allowlist is explicit

Important points:

- `isInPCModeList(...)` includes:
  - `com.smartisanos.boston.phone`
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`
  - `smt.tnt.virtual.display`
  - `com.bytedance.wirelesscast` (default virtual display package property)
  - `ScreenCastThread-display`

Relevant references:

- [SmtPCUtils.java:144](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:144)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

Interpretation:

- Smartisan explicitly recognizes a set of privileged display/session package names.
- This strongly supports the idea that official wireless TNT and Boston flows are package-tagged and special-cased.

### 14. TNT Anywhere detection is package-name based

Important points:

- `isDisplayForTntAnywhere(pkgName)` returns true for:
  - `HANDSHAKER_DISPLAY_PKG`
  - `TNT_ANYWHERE_DISPLAY_PKG`

Relevant references:

- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)
- [SmtPCUtils.java:64](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:64)
- [SmtPCUtils.java:119](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:119)

Interpretation:

- TNT Anywhere is not inferred from generic display behavior alone; it is tied to known package identities.
- based on later manual product identification, `com.smartisanos.smartfolder.aoa` is the Smartisan Handshaker package (Android/PC/Mac file transfer tooling)
- therefore `HANDSHAKER_DISPLAY_PKG` should currently be treated as a legacy/special whitelist identity, not as the primary target for reproducing TNT wireless desktop entry
- the higher-priority package for continued TNT investigation is now `com.smartisanos.tntanywhere`

### 14A. `com.smartisanos.tntanywhere` is referenced by framework/services, but its own app code is not present in the current dump

Relevant references:

- [SmtPCUtils.java:119](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:119)
- [SmtPCUtils.java:253](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:253)
- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)

Important points:

- the current repository contains framework/service references to `com.smartisanos.tntanywhere`
- but no decompiled package directory, manifest, activity, service, or explicit component launch path for that package has been found yet
- no direct `startActivity(...)`, `startService(...)`, `bindService(...)`, or `sendBroadcast(...)` targeting `com.smartisanos.tntanywhere` has been identified in the currently imported sources

Interpretation:

- `com.smartisanos.tntanywhere` is very likely a separate preinstalled/system package whose APK or jar has not yet been extracted into this workspace
- therefore, current system-side code can tell us how the package is recognized after a display exists, but not yet how the package itself initiates the official wireless TNT flow

### 15. External display discovery order is framework-defined

Important points:

- `findExtDisplayIfPossible(Context)` enumerates all displays and keeps only those where:
  - `isValidExtDisplayId(displayId)` is true
  - `isValidExtDisplayType(type, ownerPackageName)` is true
- `type == 2` displays are inserted at the head of the result list

Relevant references:

- [SmtPCUtilsInner.java:286](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:286)

Interpretation:

- This explains why display ordering and preferred display choice can depend on display type.

### 16. Official Smartisan wireless entry is a dedicated service path

Relevant references:

- [WifiDisplaySettings.java:240](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:240)
- [WifiDisplaySettings.java:246](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:246)
- [AndroidManifest.xml:7](E:/Sunshine-android-master/lebo/app/src/main/AndroidManifest.xml:7)
- [AndroidManifest.xml:39](E:/Sunshine-android-master/lebo/app/src/main/AndroidManifest.xml:39)
- [WirelessCastService.java:290](E:/Sunshine-android-master/lebo/app/src/main/java/com/bytedance/wirelesscast/WirelessCastService.java:290)
- [WirelessCastService.java:362](E:/Sunshine-android-master/lebo/app/src/main/java/com/bytedance/wirelesscast/WirelessCastService.java:362)

Important points:

- Smartisan Settings binds directly to `com.bytedance.wirelesscast/.WirelessCastService`
- that service lives inside package `com.bytedance.wirelesscast`
- `WirelessCastService` registers a `DisplayManager.DisplayListener`
- when a display is added, it explicitly records only displays whose `ownerPackageName == getPackageName()`

Interpretation:

- the official wireless-cast path is not a generic Settings-to-MediaProjection flow
- Smartisan ships a dedicated wireless-cast service package and that service itself treats display ownership identity as significant
- this strengthens the idea that display/session identity is part of the expected contract

### 16A. Miracast / Wi-Fi Display and Wireless TNT share one Settings entry surface, but diverge into different backend branches

Relevant references:

- [WifiDisplaySettingsFragment.java:20](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettingsFragment.java:20)
- [WifiDisplaySettingsFragment.java:29](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettingsFragment.java:29)
- [WifiDisplaySettings.java:330](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:330)
- [DatabaseHelper.java:323](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/settingitemsprovider/DatabaseHelper.java:323)
- [DatabaseHelper.java:666](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/settingitemsprovider/DatabaseHelper.java:666)

Important points:

- Settings exposes one common `WifiDisplaySettings` UI component
- that component is opened with `entry_from_wifi=true` for ordinary wireless display
- the same component is opened with `entry_from_wifi=false` for `Wireless TNT`
- when not entered from Wi-Fi, the page title and switch title are rewritten to `Wireless TNT`

Interpretation:

- Smartisan intentionally merged Miracast and wireless TNT into one front-end discovery/connection UI
- the user-visible entry point is shared, but the backend path taken after device selection depends on the target device type and the current TNT/display state

### 16B. Miracast itself is a physical Wi-Fi Display chain: Wi-Fi P2P + RTSP + `RemoteDisplay.listen(...)`

Relevant references:

- [DisplayManagerService.java:535](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:535)
- [WifiDisplayAdapter.java:246](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:246)
- [WifiDisplayAdapter.java:274](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:274)
- [WifiDisplayController.java:740](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:740)
- [WifiDisplayController.java:794](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:794)
- [WifiDisplayController.java:800](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:800)
- [WifiDisplayController.java:682](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayController.java:682)
- [WifiDisplayAdapter.java:159](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:159)
- [WifiDisplayAdapter.java:445](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:445)
- [WifiDisplayAdapter.java:573](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:573)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)

Important points:

- `DisplayManagerService.connectWifiDisplay(address)` delegates to `WifiDisplayAdapter.requestConnectLocked(address)`
- `WifiDisplayAdapter` owns a `WifiDisplayController`
- `WifiDisplayController` performs Wi-Fi P2P connection setup, including WPS config and group formation
- after P2P setup, it starts listening for the Miracast RTSP stream using `RemoteDisplay.listen(...)`
- when the RTSP session is established, `onDisplayConnected(surface, width, height, flags, session)` fires
- the adapter then creates a `WifiDisplayDevice` from that `Surface`
- that display device has:
  - `uniqueId = "wifi:" + macAddress`
  - `type = 3`
  - `address = DisplayAddress.fromMacAddress(mac)`
  - no explicit `ownerPackageName`

Interpretation:

- the Miracast path is fundamentally a physical external-display pipeline, not an app-owned virtual display pipeline
- this matches runtime observations like `uniqueId` starting with `wifi:`
- it also explains why physical Miracast displays usually do not carry the package-based owner identity that TNT Anywhere relies on

### 16C. TNT hooks in only after the Miracast physical display has already been admitted as a display device

Relevant references:

- [DisplayManagerService.java:722](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:722)
- [DisplayManagerService.java:737](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:737)
- [DisplayManagerService.java:1282](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:1282)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:258](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:258)

Important points:

- once any display device is added, `DisplayManagerService.handleDisplayDeviceAddedLocked(...)` runs
- if PC/TNT support is active, that method forwards the newly added device into TNT display logic
- TNT then decides whether the device is a valid base external display and whether to create `smt.tnt.virtual.display`
- later, during display configuration, `showVirtualDisplayIfNeededLocked(...)` may route visible content to:
  - the default display
  - the base physical Miracast display
  - or the TNT virtual display layered on top

Interpretation:

- TNT does not replace the Miracast transport layer
- instead, Miracast first creates a normal external display, and TNT then opportunistically builds its own desktop layer on top of that admitted display
- this strongly supports the model:
  - Miracast/Wi-Fi Display is the transport and base-display admission layer
  - TNT is the later desktop virtualization and routing layer

### 16D. Settings distinguishes "ordinary Miracast wireless connect" from TNT virtual modes in policy checks

Relevant references:

- [SmtTntUtil.java:59](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:59)
- [SmtTntUtil.java:68](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:68)
- [SmtTntUtil.java:84](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:84)
- [WifiDisplaySettings.java:413](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wfd/WifiDisplaySettings.java:413)
- [WifiEnabler.java:209](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wifi/WifiEnabler.java:209)
- [WifiApEnablerEx.java:148](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/wifi/WifiApEnablerEx.java:148)

Important points:

- `isMiracastWirelessConnect(...)` checks for:
  - current ext display type `== 3`
  - a matching `MediaRouter` route name
  - route status code `== 6`
- `isSmtDisplayVirtualMode(...)` treats display type `3` or `5` as virtual/wireless-TNT-related modes
- several Settings policies use these helpers to gate Wi-Fi, hotspot, and TNT shutdown/reboot prompts

Interpretation:

- Smartisan's Settings layer explicitly knows that:
  - plain Miracast-connected physical displays are one class of state
  - TNT-related virtual/desktop states are another class
- so even though both states are exposed through the same UI surface, the policy layer still treats them differently

### 16E. A Miracast base display is accepted by TNT primarily through `displayId` and `type`, not through Miracast session metadata

Relevant references:

- [SmtPCUtilsSmtBase.java:56](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:56)
- [SmtPCUtilsInner.java:286](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:286)
- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [WifiDisplayAdapter.java:573](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:573)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:287](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:287)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

Important points:

- framework-level `isValidExtDisplayId(displayId)` only requires:
  - `displayId != -1`
  - `displayId != 0`
- framework-level `isValidExtDisplayType(type, ownerPackageName)` accepts:
  - `type == 2`
  - `type == 3`
  - `type == 5` only when `ownerPackageName` is in `PC_MODE_LIST`
  - `type == 4` only for overlay test mode
- a physical Miracast `WifiDisplayDevice` is created with:
  - `type = 3`
  - `uniqueId = "wifi:" + mac`
  - `address = DisplayAddress.fromMacAddress(mac)`
  - no explicit `ownerPackageName`
- therefore a normal Miracast display already satisfies the TNT base-display type gate:
  - `isValidExtDisplayType(3, null) == true`
- `TntDisplayManagerServiceImpl.addVirtualDisplayLocked(...)` then additionally requires:
  - device name is not already `smt.tnt.virtual.display`
  - there is no existing base display already locked in, unless force-update is active
  - a matching logical display can be found for the device
- no current TNT admission check uses:
  - Miracast `WifiDisplaySessionInfo`
  - `groupId`
  - `sessionId`
  - `custom_key_wireless_cast_name`
  - `wifi:` uniqueId prefix itself
  - MAC address contents

Interpretation:

- a Miracast display is accepted as a TNT base display for surprisingly simple reasons:
  - it is a non-default display
  - and its display type is `3`
- at the "can this become the TNT base display?" layer, Smartisan does not appear to require any special Miracast session metadata
- the more restrictive conditions happen later, at actual TNT entry time:
  - boot/provision state must be ready
  - the display must already be tracked in the ext-display manager
  - no display is currently in PC mode
  - and either `pc_mode_enable == 1` or TNT Anywhere mode is active
- this means the main gap between plain Miracast and real TNT entry is not the Miracast transport handshake itself, but the later system-mode transition conditions

### 16F. `isValidExtDisplayType(...)` is really a filter over concrete Android display backends

Relevant references:

- [Display.java:56](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/view/Display.java:56)
- [Display.java:550](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/view/Display.java:550)
- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [LocalDisplayAdapter.java:458](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/LocalDisplayAdapter.java:458)
- [LocalDisplayAdapter.java:464](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/LocalDisplayAdapter.java:464)
- [WifiDisplayAdapter.java:626](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:626)
- [OverlayDisplayAdapter.java:299](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:299)
- [VirtualDisplayAdapter.java:375](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:375)

Important points:

- Android `Display` type constants are:
  - `0 = UNKNOWN`
  - `1 = INTERNAL`
  - `2 = EXTERNAL`
  - `3 = WIFI`
  - `4 = OVERLAY`
  - `5 = VIRTUAL`
- `LocalDisplayAdapter` assigns:
  - `type = 1` for internal/local built-in displays
  - `type = 2` for non-internal local physical displays
- `WifiDisplayAdapter.WifiDisplayDevice` assigns:
  - `type = 3`
- `OverlayDisplayAdapter.OverlayDisplayDevice` assigns:
  - `type = 4`
- `VirtualDisplayAdapter.VirtualDisplayDevice` assigns:
  - `type = 5`
- TNT's framework gate `isValidExtDisplayType(type, ownerPackageName)` then interprets them as:
  - `type == 2`: wired physical external display, always accepted
  - `type == 3`: Wi-Fi display / Miracast physical display, always accepted
  - `type == 5`: app/system virtual display, accepted only when `ownerPackageName` is in Smartisan's PC-mode allowlist
  - `type == 4`: overlay test display, accepted only when `persist.easycast.show_overlay_display=true`
  - `type == 1` or `0`: not treated as TNT external-display candidates

Interpretation:

- in practice, `isValidExtDisplayType(...)` is not a vague policy hook; it is classifying real backend families:
  - built-in phone/tablet panels
  - wired HDMI/DP-style outputs
  - Miracast/Wi-Fi Display outputs
  - developer overlay test displays
  - app-created virtual displays
- this also explains why plain Miracast works with so little extra metadata:
  - it already lands in the privileged physical-external bucket `type == 3`
- and it explains why third-party virtual displays are much harder to use for TNT:
  - they land in `type == 5`, which is gated by package identity

### 17. HPPlay / Lelink mirror backend really creates `ScreenCastThread-display`

Relevant references:

- [PermissionBridgeActivity.java:150](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/permission/PermissionBridgeActivity.java:150)
- [PermissionBridgeActivity.java:152](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/permission/PermissionBridgeActivity.java:152)
- [MirrorManagerImpl.java:132](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:132)
- [MirrorManagerImpl.java:142](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:142)
- [MirrorManagerImpl.java:161](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/MirrorManagerImpl.java:161)
- [ScreenCastService.java:416](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:416)
- [ScreenCastService.java:531](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:531)
- [ScreenCastService.java:538](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/ScreenCastService.java:538)
- [g.java:536](E:/Sunshine-android-master/lebo/app/src/main/java/com/hpplay/sdk/source/mirror/g.java:536)
- [i.java:97](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/hpplay/sdk/source/mirror/i.java:97)
- [i.java:779](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/hpplay/sdk/source/mirror/i.java:779)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

Important points:

- the hpplay stack first obtains `MediaProjection` through `PermissionBridgeActivity`
- it then starts and binds `ScreenCastService`
- `ScreenCastService` converts the permission `Intent` into a real `MediaProjection`
- that `MediaProjection` is passed into the mirror worker thread `g`
- both the standalone wireless-cast app and the TNT Go app's Lebo path call:
  - `MediaProjection.createVirtualDisplay("ScreenCastThread-display", ...)`
- Smartisan's PC-mode allowlist explicitly contains `ScreenCastThread-display`

Interpretation:

- `ScreenCastThread-display` is not an accidental string; it is a known official backend identity
- Smartisan intentionally recognizes this mirror backend family
- however, most framework/service validation sites still pass `ownerPackageName`, not display name, into `isValidExtDisplayType(...)`
- therefore the display name is a recognized helper identity, but package ownership still appears to be the stronger admission signal in the framework

### 17A. In the official wireless virtual-display flow, `ownerPackageName` comes from the caller app package

Relevant references:

- [MediaProjection.java:72](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/media/projection/MediaProjection.java:72)
- [MediaProjection.java:73](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/media/projection/MediaProjection.java:73)
- [DisplayManagerGlobal.java:344](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/hardware/display/DisplayManagerGlobal.java:344)
- [DisplayManagerService.java:609](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:609)
- [DisplayManagerService.java:615](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:615)
- [VirtualDisplayAdapter.java:66](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:66)
- [VirtualDisplayAdapter.java:200](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:200)
- [VirtualDisplayAdapter.java:379](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/VirtualDisplayAdapter.java:379)
- [WifiDisplayAdapter.java:613](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/WifiDisplayAdapter.java:613)

Important points:

- `MediaProjection.createVirtualDisplay(...)` eventually calls `DisplayManagerGlobal.createVirtualDisplay(context, ...)`
- `DisplayManagerGlobal` passes `context.getPackageName()` into `IDisplayManager.createVirtualDisplay(...)`
- `DisplayManagerService.createVirtualDisplayInternal(...)` forwards that package name into `VirtualDisplayAdapter.createVirtualDisplayLocked(...)`
- `VirtualDisplayAdapter.VirtualDisplayDevice` stores that package string as `mOwnerPackageName`
- `VirtualDisplayAdapter.getDisplayDeviceInfoLocked()` writes:
  - `mInfo.ownerPackageName = mOwnerPackageName`
- therefore, in the official `lebo/hpplay` wireless virtual-display path, the expected owner package is `com.bytedance.wirelesscast`
- by contrast, the physical `WifiDisplayAdapter` display-info path does not populate `ownerPackageName`

Interpretation:

- for Smartisan's official wireless virtual-display flow, `ownerPackageName` is not synthesized by TNT code later
- it is inherited from the package context of the app/service that actually called `createVirtualDisplay(...)`
- this means runtime observations such as `ownerPackageName = com.bytedance.wirelesscast`, `com.smartisanos.smartfolder.aoa`, or `com.smartisanos.tntanywhere` are strong clues about which high-level entry path really created the display
- it also cleanly separates two classes of displays:
  - physical Miracast / WifiDisplay displays, where `ownerPackageName` is typically not the main identity signal
  - app-created virtual displays, where `ownerPackageName` is a first-class framework-visible identity
- with the current product knowledge, `com.smartisanos.smartfolder.aoa` should be interpreted cautiously as a Handshaker-related compatibility identity, while `com.smartisanos.tntanywhere` remains the more likely package to represent the TNT wireless-desktop-specific path

### 17B. `tntanywhere` changes TNT behavior at the system-service level even when `pc_mode_enable` is not set

Relevant references:

- [DisplayManagerService.java:739](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:739)
- [DisplayManagerService.java:804](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:804)
- [TntDisplayManagerServiceImpl.java:258](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:258)
- [TntDisplayManagerServiceImpl.java:278](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:278)
- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [TntManagerService.java:1436](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1436)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntManagerService.java:3922](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3922)
- [TntManagerService.java:3958](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:3958)
- [TntManagerService.java:4063](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:4063)
- [TntManagerService.java:4069](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:4069)

Important points:

- `DisplayManagerService` notifies TNT display logic whenever a display with some `ownerPackageName` is added or removed
- `TntDisplayManagerServiceImpl.checkDisplayForTntAnywhere(...)` flips `mInTntAnywhereMode` when that owner package is recognized as TNT Anywhere
- in mirror routing, `showVirtualDisplayIfNeededLocked(...)` returns the TNT virtual display instead of default display when the base display owner package is `tntanywhere`
- in `TntManagerService.handleDisplayAdded(...)`, TNT entry is allowed when either:
  - `mDisplayMode == 1`
  - or `mTntDisplayMS.getInTntAnywhereMode()` is true
- `handleDisplayModeChanged(0)` also avoids the usual path reset when TNT Anywhere mode is active
- keep-alive logic likewise treats TNT Anywhere as equivalent to active PC-mode for several decisions

Interpretation:

- `com.smartisanos.tntanywhere` is not just a passive allowlist string
- once a display owned by that package appears, system services treat it as a privileged TNT wireless-desktop identity
- this is the strongest current evidence that reproducing the official TNT wireless path likely requires either:
  - the actual `com.smartisanos.tntanywhere` package
  - or faithfully emulating the package/display identity and the creation sequence that package uses

### 17C. `TntExtendDisplayManager` mostly tracks TNT virtual displays, not raw physical base displays

Relevant references:

- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)
- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1416](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1416)
- [TntManagerService.java:1424](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1424)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntExtendDisplayManager.java:84](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:84)
- [TntExtendDisplayManager.java:242](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:242)
- [TntExtendDisplayManager.java:262](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntExtendDisplayManager.java:262)

Important points:

- `RootWindowContainer.onDisplayAdded()` forwards every non-default display to `scheduleDisplayAdded(displayId, ..., newDisplay=true)`
- but `TntManagerService.scheduleDisplayAdded(...)` immediately rejects `displayId < 100000`:
  - `"don't process non virtual displayId"`
- when the new display is `>= 100000`, `scheduleDisplayAdded(...)`:
  - inserts it into `mTntExtDisplayManager`
  - then chooses `getPreferredDisplayIdIfPossible()`
  - then posts the eventual `handleDisplayAdded(...)`
- `TntExtendDisplayManager.rebuildDisplayList(...)` also only re-adds displays whose id is `>= 100000`
- `getPreferredDisplayIdIfPossible()` simply returns the first entry in that list
- `handleDisplayAdded(...)` will only enter TNT if `mTntExtDisplayManager.get(displayId) != null`

Interpretation:

- despite its name, `TntExtendDisplayManager` is not the authoritative registry of all physical external displays
- it behaves much more like:
  - the candidate queue of TNT-layer displays
  - which in practice means the remapped `100000+` `smt.tnt.virtual.display` instances
- the lower-ID physical display still matters, but mostly inside `TntDisplayManagerServiceImpl` as:
  - `mBaseDisplay`
  - `mBaseDevice`
  - `mBaseDisplayId`
- this cleanly splits the TNT pipeline into two stages:
  1. a physical or privileged virtual display is admitted as the base display
  2. the TNT service stack creates and then tracks its own `100000+` virtual display as the real PC-mode object

### 17D. Overlay display has now been experimentally confirmed as a working headless TNT base-display path

Relevant references:

- [SmtPCUtils.java:145](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:145)
- [SmtPCUtils.java:268](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:268)
- [OverlayDisplayAdapter.java:77](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:77)
- [OverlayDisplayAdapter.java:100](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/OverlayDisplayAdapter.java:100)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

Experiment result:

- using ADB/root to:
  - enable `persist.easycast.show_overlay_display=1`
  - create an Android overlay display through `Settings.Global["overlay_display_devices"]`
- the device was able to headlessly start TNT successfully in real testing

Interpretation:

- this is a major confirmation that Smartisan's TNT stack does not fundamentally require a real wired monitor or Miracast sink
- instead, it is sufficient for system services to see a display that:
  - is admitted by Smartisan's external-display policy
  - survives base-display selection
  - allows TNT to create its own `smt.tnt.virtual.display`
- importantly, this working path uses the platform's own official overlay-display debug mechanism
- so this is not a fragile "fake hardware" trick in the narrow sense
- it is better understood as:
  - reusing Android's official debug display backend
  - while enabling Smartisan's built-in overlay acceptance gate
- this sharply narrows the remaining productization problem:
  - not "can TNT run headless at all?"
  - but "how can `app-mirror` invoke the same system-recognized path automatically and safely?"

### 18. Official Boston app actively writes `pc_mode_enable`

Relevant references:

- [Constants.java:16](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/utils/Constants.java:16)
- [Constants.java:24](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/utils/Constants.java:24)
- [EasyCastSourceLebo.java:1018](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceLebo.java:1018)
- [EasyCastSourceByte.java:316](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceByte.java:316)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

Important points:

- EasyCast constants define:
  - mirror mode = `0`
  - PC mode = `1`
- both official EasyCast source implementations write `Settings.Secure["pc_mode_enable"]`
- `TntManagerService.handleDisplayAdded(...)` requires either:
  - `pc_mode_enable == 1`
  - or TNT Anywhere mode is active

Interpretation:

- the official app does not rely on display creation alone
- it also cooperates with the system by pre-setting the persistent display mode preference that TNT services later check
- this is one of the most concrete differences between the official flow and a generic third-party projection flow

### 19. Official Boston control flow distinguishes wired and wireless before starting TNT

Relevant references:

- [ConnectionReceiver.java:220](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:220)
- [ConnectionReceiver.java:240](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:240)
- [ConnectionReceiver.java:668](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/receiver/ConnectionReceiver.java:668)
- [SmtTntUtil.java:21](E:/Sunshine-android-master/smartisanos/settings/app/src/main/java/com/android/settings/utils/SmtTntUtil.java:21)

Important points:

- the Boston app exposes privileged broadcast actions such as:
  - `com.smartisanos.boston.action.START_TNT`
  - `com.smartisanos.boston.action.REBOOT_TNT`
  - `com.smartisanos.boston.action.SHUTDOWN_TNT`
- its receiver first decides whether current mode is wired or wireless
- wired mode goes through `DpCtrlUtils.startupDp()/shutdownDp()`
- wireless mode goes through `EasyCastSwitcher`
- Settings-side helper `SmtTntUtil` also sends these actions with `displayType = wired|wifi`

Interpretation:

- official TNT control is a coordinated multi-path state machine, not a single "start desktop" API
- Smartisan separates wired DP-style entry and wireless EasyCast entry early in the control path
- reproducing official behavior likely requires matching the correct branch, not just creating a display

### 20. `global_pc_mode_settings` is observed by the official app, not used as its root trigger

Relevant references:

- [ConnectionService.java:248](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/service/ConnectionService.java:248)
- [ConnectionService.java:255](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/smartisanos/boston/phone/service/ConnectionService.java:255)
- [TntManagerService.java:1268](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1268)

Important points:

- `ConnectionService` registers a content observer for `global_pc_mode_settings`
- when it changes, the service refreshes `CastHalWrapper` state and logs mode changes
- the same key is written by `TntManagerService` during real enter/exit

Interpretation:

- even in the official app, `global_pc_mode_settings` behaves like a downstream state signal
- this matches the earlier system-side conclusion that writing this key alone is not the true TNT entry mechanism

### 21. Smartisan grants additional package-based privileges beyond display admission

Relevant references:

- [TntPowerManagerServiceImpl.java:52](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/power/TntPowerManagerServiceImpl.java:52)
- [TntMediaProjectionManagerServiceImpl.java:19](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/media/projection/TntMediaProjectionManagerServiceImpl.java:19)
- [TntDisplayManagerServiceImpl.java:478](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:478)
- [SmtPCUtilsInner.java:463](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:463)

Important points:

- power manager side marks `ownerPackageName == com.smartisanos.boston.phone` as a special Boston display
- media-projection manager exempts `isInPCModeList(...)` packages from ordinary projection limits while in PC mode
- display manager tracks TNT Anywhere mode based on special package identities:
  - `com.smartisanos.smartfolder.aoa`
  - `com.smartisanos.tntanywhere`

Interpretation:

- Smartisan special-cases official TNT-related identities in multiple subsystems, not just one
- this makes it less likely that a third-party package can fully impersonate the official path by reproducing only one surface-level behavior

## Complete Call Chain

The currently confirmed TNT activation call chain is:

1. `SystemServer` creates the TNT manager service through `TntFeatureFactoryImpl`
2. `SystemServer` registers that service as binder service `smt_pcm`
3. framework-side `SmtPCUtils*` APIs connect to `smt_pcm` through `ISmtPCManager`
4. `TntManagerService` is the actual binder implementation behind `ISmtPCManager`
5. `DisplayManagerService.handleDisplayDeviceAddedLocked()` receives a new display device
6. if TNT support is active, `DisplayManagerService` forwards the device to `TntDisplayManagerServiceImpl`
7. `TntDisplayManagerServiceImpl.addVirtualDisplayLocked()` checks whether the new device is a valid TNT base display
8. if valid, the service records that base display and creates `smt.tnt.virtual.display`
9. `DisplayManagerService.addLogicalDisplayLocked()` remaps that TNT virtual display to `100000+`
10. `RootWindowContainer.onDisplayAdded()` sees the new logical display and calls `mService.mTNT.scheduleDisplayAdded(displayId, ...)`
11. `TntManagerService.scheduleDisplayAdded()` only really processes `100000+` TNT virtual display IDs
12. `TntManagerService.LocalHandler` handles message `2` and runs `handleDisplayAdded(displayId)`
13. `handleDisplayAdded(...)` checks boot/provision/display-mode/TNT-Anywhere state and decides whether TNT may enter
14. if conditions pass, `TntActivityTaskManagerServiceImpl.enterPcMode(displayId)` is triggered
15. `TntActivityTaskManagerServiceImpl` calls:
    - `SmtPCUtilsInner.setIsPcMode(displayId, true)`
    - `mTntWindowManager.enterPcMode(display, true)`
16. `TntManagerService.enterPCModeLocked(displayId)` finishes the real system-mode transition
17. only after that does the system write `global_pc_mode_settings = 1`

Key references:

- [SystemServer.java:1667](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/SystemServer.java:1667)
- [SystemServer.java:1671](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/SystemServer.java:1671)
- [SmtPCUtilsSmtBase.java:39](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsSmtBase.java:39)
- [TntManagerService.java:130](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:130)
- [DisplayManagerService.java:722](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:722)
- [DisplayManagerService.java:737](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:737)
- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)
- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [DisplayManagerService.java:852](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:852)
- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)
- [TntManagerService.java:1414](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1414)
- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)

## Conditions Required To Enter TNT

Based on the currently confirmed code, a real TNT session requires all of the following classes of conditions:

### A. A display must first be recognized as a valid TNT base display

The system must see a display whose type/package matches Smartisan's validation rules:

- `type == 2`
- `type == 3`
- `type == 5` and package name is in the Smartisan PC-mode allowlist
- `type == 4` only for overlay-display test mode

Relevant references:

- [SmtPCUtils.java:263](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:263)
- [SmtPCUtils.java:252](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtils.java:252)

### B. The display must survive TNT base-display filtering

`TntDisplayManagerServiceImpl.addVirtualDisplayLocked()` rejects displays that:

- fail `isValidExtDisplayType(...)`
- are already the TNT virtual display itself
- are overlay-display test devices in the wrong mode
- arrive when a base display is already locked in and no force-refresh is pending

Relevant references:

- [TntDisplayManagerServiceImpl.java:184](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:184)

### C. The system must create the TNT virtual display

Even after a valid base display exists, TNT still does not enter until:

- `smt.tnt.virtual.display` is created
- the logical display is remapped to `100000+`

Relevant references:

- [TntDisplayManagerServiceImpl.java:217](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:217)
- [TntDisplayManagerServiceImpl.java:290](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/display/TntDisplayManagerServiceImpl.java:290)

### D. The display-added event must reach `TntManagerService`

The TNT virtual display must be:

- materialized as a logical display
- delivered to `RootWindowContainer.onDisplayAdded()`
- forwarded into `scheduleDisplayAdded()`

Relevant references:

- [DisplayManagerService.java:851](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/display/DisplayManagerService.java:851)
- [RootWindowContainer.java:2418](E:/Sunshine-android-master/smartisanos/servicesjar/sources/com/android/server/wm/RootWindowContainer.java:2418)

### E. `TntManagerService.handleDisplayAdded()` gate conditions must pass

At entry time, TNT still requires:

- boot completed or locked-boot completed
- display is tracked by `TntExtendDisplayManager`
- no display is already in PC mode
- either `pc_mode_enable == 1` or TNT Anywhere mode is active
- device provisioned
- valid home / required services available
- no "switch home" dialog blocking the transition

Relevant references:

- [TntManagerService.java:1435](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1435)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)
- [TntManagerService.java:1439](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1439)

### F. Framework/system state must then be switched into PC mode

Once entry is accepted, TNT still needs:

- ATMS/WMS PC-mode entry
- framework `sDisplayIdInPcMode` to be updated
- `enterPCModeLocked()` to complete

Relevant references:

- [TntActivityTaskManagerServiceImpl.java:510](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:510)
- [TntActivityTaskManagerServiceImpl.java:519](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/wm/TntActivityTaskManagerServiceImpl.java:519)
- [SmtPCUtilsInner.java:278](E:/Sunshine-android-master/smartisanos/frameworkjar/sources/android/app/SmtPCUtilsInner.java:278)
- [TntManagerService.java:1253](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1253)

### G. For the normal non-TNT-Anywhere path, `pc_mode_enable` must already agree

The official Boston / EasyCast stack explicitly writes:

- `Settings.Secure["pc_mode_enable"] = 1` for PC mode
- `Settings.Secure["pc_mode_enable"] = 0` for mirror mode

and the TNT entry gate checks:

- `mDisplayMode == 1`
- or `getInTntAnywhereMode() == true`

Relevant references:

- [EasyCastSourceLebo.java:1018](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceLebo.java:1018)
- [EasyCastSourceByte.java:316](E:/Sunshine-android-master/TNTgo_1.0.3_App/app/src/main/java/com/easycast/source/EasyCastSourceByte.java:316)
- [TntManagerService.java:1438](E:/Sunshine-android-master/smartisanos/smartisan-services-tnt/sources/com/android/server/pc/TntManagerService.java:1438)

Interpretation:

- if we are not entering via the special TNT Anywhere path, then a valid display alone is still insufficient unless the persistent display-mode state also says PC mode is enabled

## Current Bottom-Line Judgment

The current best-supported judgment is:

1. `global_pc_mode_settings` is not a sufficient trigger.
2. A normal third-party display is not enough by itself.
3. The system expects a valid Smartisan-recognized base display first.
4. The system then creates its own TNT virtual display and only reacts to that layer for TNT entry.
5. Official Smartisan wireless/Boston/TNT-Anywhere paths work partly because their display type and package identity already fit this model.
6. The official app stack also writes `pc_mode_enable`, routes through wired vs wireless control branches, and benefits from additional package-based special handling in power/media-projection/display services.

This means the core challenge for `app-mirror` is not just "open TNT" but "make the system believe a valid TNT-eligible display/session exists, so the native TNT service stack completes its own activation path."
