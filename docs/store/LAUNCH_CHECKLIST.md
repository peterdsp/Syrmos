# Store Launch Checklist

## App Store (iOS)

### App Store Connect Setup
- [ ] Create new app in App Store Connect (com.syrmos.ios)
- [ ] Set primary language: English (U.S.)
- [ ] Set category: Travel (primary), Navigation (secondary)
- [ ] Set content rating: 4+
- [ ] Set pricing: Free

### App Information
- [ ] App name: **Syrmos - Athens Rail Times**
- [ ] Subtitle: **Athens Metro & Tram Times**
- [ ] Promotional text (from docs/store/listing.md)
- [ ] Description (from docs/store/listing.md)
- [ ] Keywords: athens,metro,transit,tram,train,subway,rail,departure,greece,stasy
- [ ] Privacy policy URL: host docs/PRIVACY_POLICY.md (e.g. syrmos.peterdsp.dev/privacy)
- [ ] Support URL: syrmos.peterdsp.dev

### Screenshots (required)
- [ ] iPhone 6.9" (iPhone 16 Pro Max): 1320x2868, minimum 3 screenshots
- [ ] iPhone 6.7" (iPhone 15 Plus): 1290x2796, minimum 3 screenshots
- [ ] iPad 13" (optional but recommended)
- Screenshots to capture: Home with departures, Lines list, Map with trains, Station detail

### Build & Submit
- [ ] Generate signing certificate and provisioning profile
- [ ] Archive from Xcode (Product > Archive)
- [ ] Upload to App Store Connect via Xcode Organizer
- [ ] Fill in "What's New" for v1.0.0
- [ ] Submit for review

---

## Google Play (Android)

### Play Console Setup
- [ ] Create app in Google Play Console
- [ ] App name: **Syrmos - Athens Rail Times**
- [ ] Default language: English (United States)
- [ ] App type: App (not game)
- [ ] Free or paid: Free
- [ ] Content rating questionnaire

### Store Listing
- [ ] Short description (from docs/store/listing.md)
- [ ] Full description (from docs/store/listing.md)
- [ ] App icon: 512x512 PNG (use androidApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png, scaled up)
- [ ] Feature graphic: 1024x500 PNG
- [ ] Phone screenshots: minimum 2, recommended 4-8
- [ ] Privacy policy URL: host docs/PRIVACY_POLICY.md

### App Signing
- [ ] Generate upload keystore: `keytool -genkey -v -keystore syrmos-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias syrmos`
- [ ] Add signing config to androidApp/build.gradle.kts
- [ ] Build signed AAB: `./gradlew :androidApp:bundleRelease`
- [ ] Enroll in Google Play App Signing (recommended)

### Release
- [ ] Upload AAB to internal testing track first
- [ ] Test on multiple devices
- [ ] Promote to production
- [ ] Fill in release notes

---

## Both Platforms

### Before Submit
- [ ] Host privacy policy at a public URL
- [ ] Take fresh screenshots on both platforms with data populated
- [ ] Test offline mode (airplane mode)
- [ ] Test GPS station detection
- [ ] Test all tabs: Home, Lines, Map, Settings
- [ ] Verify train simulation is running on the map
- [ ] Test bilingual toggle (English/Greek)

### Post-Launch
- [ ] Update README with actual store links
- [ ] Update badge SVGs with real URLs
- [ ] Monitor crash reports
- [ ] Respond to any store review feedback
