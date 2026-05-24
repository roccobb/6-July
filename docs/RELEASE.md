# Release Checklist

## 1. Developer Account

Create or use a Google Play Console developer account for Valkotassu.

For new personal developer accounts, Google Play may require a closed test with at least 12 opted-in testers for 14 continuous days before production access.

## 2. Package Name

The app uses:

```text
com.valkotassu.finnishwordoftheday
```

Do not change this after publishing unless you are intentionally creating a different app.

## 3. Release Keystore

Create a local release keystore. Do not commit it to Git.

```sh
mkdir -p release
keytool -genkeypair \
  -v \
  -keystore release/valkotassu-release.jks \
  -alias valkotassu \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Copy the template:

```sh
cp keystore.properties.example keystore.properties
```

Then edit `keystore.properties` with your real passwords.

Back up both `release/valkotassu-release.jks` and the passwords somewhere safe.

## 4. Build App Bundle

Google Play expects an Android App Bundle (`.aab`).

```sh
env JAVA_HOME=/snap/android-studio/209/jbr ./gradlew bundleRelease
```

The output should be:

```text
app/build/outputs/bundle/release/app-release.aab
```

## 5. Play Console Store Listing

Prepare:

- App name
- Short description
- Full description
- App icon
- Phone screenshots
- Category: Education
- Privacy policy URL
- Data safety form

## 6. Data Safety

Current app behavior:

- No personal data collected
- No data shared
- No network requests
- Favorites stored locally on device only

Confirm this in Play Console's Data safety section.
