# Deep Linking Implementation - Complete

## What Was Implemented

✅ **URL Scheme Configuration** (AndroidManifest.xml)
- `solverapp://qr/{command}/{tag}` - QR/NFC custom scheme
- `https://solver.no/qr/{command}/{tag}` - Universal Links (App Links)
- `solverapp://oauth/vipps` - Vipps OAuth (existing)

✅ **Core Deep Linking System**
- `DeepLink.kt` - Sealed interface for deep link types
- `DeepLinkParser.kt` - Parses both custom scheme and Universal Links
- `DeepLinkViewModel.kt` - Handles QR command execution with middleware
- Integrated with `MainActivity` via intent handling

✅ **Tag-Based API Operations**
- `TagRepository.kt` - Fetch object by tag, execute command by tag
- Added endpoints to `SolverApiService`:
  - `GET /api/Object/Tag/{tagId}`
  - `PUT /api/Object/Execute/{tag}/{command}`

✅ **Command Execution UI**
- Loading overlay (matches iOS "Executing command..." UI)
- Status bottom sheet (shared `StatusBottomSheetContent`)
- Error snackbar for failed operations
- Middleware processing (same as button press)

## How It Works

### User Flow
1. **User scans QR code with camera app**
   - QR contains: `solverapp://qr/unlock/abc-123-def`
   - Or: `https://solver.no/qr/unlock/abc-123-def`
   - System opens SolverApp

2. **OR User taps NFC tag**
   - Android automatically reads NFC URL
   - Opens SolverApp directly

3. **App receives intent**
   - `MainActivity.onCreate` or `onNewIntent` catches it
   - `DeepLinkParser.parse(uri)` extracts command/tag
   - `DeepLinkViewModel.handle(uri)` executes

4. **DeepLinkViewModel executes**
   - Fetches object by tag: `GET /api/Object/Tag/{tag}`
   - Executes command: `PUT /api/Object/Execute/{tag}/{command}`
   - Processes middleware (payment, geofence, Danalock, etc.)
   - Shows result with StatusBottomSheet

### Deep Link Formats

**Custom Scheme:**
```
solverapp://qr/{command}/{tag}
```

**Universal Link (App Links):**
```
https://solver.no/qr/{command}/{tag}
```

**Examples:**
- `solverapp://qr/unlock/abc-123`
- `https://solver.no/qr/lock/def-456`
- `solverapp://qr/status/xyz-789`

## Files Created

### Core Deep Linking
- `app/src/main/java/no/solver/solverappdemo/core/deeplink/DeepLink.kt`
- `app/src/main/java/no/solver/solverappdemo/core/deeplink/DeepLinkParser.kt`
- `app/src/main/java/no/solver/solverappdemo/core/deeplink/DeepLinkViewModel.kt`

### Data Layer
- `app/src/main/java/no/solver/solverappdemo/data/repositories/TagRepository.kt`

### UI Components
- `app/src/main/java/no/solver/solverappdemo/ui/components/StatusBottomSheetContent.kt`

## Files Modified

- `app/src/main/AndroidManifest.xml` - Added intent filters
- `app/src/main/java/no/solver/solverappdemo/MainActivity.kt` - Intent handling
- `app/src/main/java/no/solver/solverappdemo/ui/navigation/AppNavHost.kt` - Overlay UI
- `app/src/main/java/no/solver/solverappdemo/data/api/SolverApiService.kt` - Tag endpoints

## Testing Deep Links

### Method 1: ADB Command
```bash
# Custom scheme
adb shell am start -a android.intent.action.VIEW -d "solverapp://qr/unlock/test-tag-123" no.solver.solverapp

# Universal Link
adb shell am start -a android.intent.action.VIEW -d "https://solver.no/qr/unlock/test-tag-123" no.solver.solverapp
```

### Method 2: Browser
1. Open Chrome
2. Navigate to: `solverapp://qr/unlock/test-tag-123`
3. App should open

### Method 3: Test with Real QR Code
1. Generate QR code containing: `solverapp://qr/unlock/YOUR_TAG`
2. Scan with camera app
3. Tap notification to open SolverApp

## Universal Links (App Links) Setup

For Universal Links (`https://solver.no/qr/...`) to work:

### 1. Server Configuration
Host `/.well-known/assetlinks.json` on `https://solver.no`:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "no.solver.solverapp",
    "sha256_cert_fingerprints": [
      "YOUR_SIGNING_KEY_SHA256_FINGERPRINT"
    ]
  }
}]
```

### 2. Get Signing Key Fingerprint
```bash
# Debug key
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256

# Release key (from Play Console or your keystore)
keytool -list -v -keystore your-keystore.jks -alias your-alias | grep SHA256
```

### 3. Verify App Links
```bash
adb shell pm get-app-links no.solver.solverapp
```

## API Endpoints Used

### Fetch Object by Tag
```
GET /api/Object/Tag/{tagId}
→ Returns SolverObject with commands, permissions, etc.
```

### Execute Command by Tag
```
PUT /api/Object/Execute/{tag}/{command}
Body: { latitude, longitude } // optional location
→ Returns ExecuteResponse with context for middleware
```

## How to Create QR Codes

Use any QR generator and encode:
```
solverapp://qr/unlock/YOUR_ACTUAL_TAG_ID
```

Or for Universal Links:
```
https://solver.no/qr/unlock/YOUR_ACTUAL_TAG_ID
```

The tag ID should match what's in your Solver backend.

## Architecture Notes

### Deduplication
- Same URI within 2 seconds is ignored (prevents double execution)
- Tracked via `lastHandledUri` and `lastHandledTimestamp`

### Middleware Reuse
- Deep link execution uses the SAME middleware chain as button press
- PaymentMiddleware, SubscriptionMiddleware, DanalockMiddleware, etc.
- Ensures consistent behavior for all command sources

### UI Consistency
- Loading overlay matches iOS (dim + centered card)
- Status sheet uses shared `StatusBottomSheetContent`
- Error handling via Snackbar

## Comparison with iOS

| Feature | iOS | Android |
|---------|-----|---------|
| Custom Scheme | `solverapp://` in Info.plist | Intent filter in Manifest |
| Universal Links | Associated Domains entitlement | App Links with assetlinks.json |
| Parser | `DeepLinkParser.swift` | `DeepLinkParser.kt` |
| Handler | `DeepLinkHandler.swift` (singleton) | `DeepLinkViewModel` (Hilt) |
| Loading UI | `.overlay` modifier | `Box` with scrim overlay |
| Status Sheet | `.sheet` modifier | `ModalBottomSheet` |
| Middleware | Same chain as buttons | Same chain as buttons |
