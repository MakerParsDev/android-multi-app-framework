# Local UMP debug-geography testing

The UMP SDK requires an explicitly registered test device to simulate EEA or
regulated US geography on **physical Android devices**. Merely selecting "Force
EEA" in the app's debug tools does not register the device. Emulators are
automatically treated as UMP test devices by supported SDK versions.

1. Build/run the **debug** variant with Google test ad units.
2. Make a consent info request and inspect the device's local logcat for the
   Google UMP SDK prompt mentioning `addTestDeviceHashedId(...)`.
3. On a private workstation, pass **only this hashed test-device ID**, not the
   raw Android identifier, to the debug build as a Gradle project property:
   `ORG_GRADLE_PROJECT_UMP_TEST_DEVICE_HASH=<private 32-hex hash>`
   `./gradlew :app:assembleIsmiazamduasiDebug`
   Do not store it in committed files, issue comments, CI logs or release
   artifacts. Leave the environment property unset for standard builds.
4. Install only on your controlled test device. In Settings > Ads Debug,
   choose Force EEA (this resets only the test app's UMP consent), then show
   the consent form. The diagnostic warns when no test hash was supplied.
   An actual form also depends on the corresponding Google-certified
   message being configured/published for that Firebase/AdMob app.
5. Verify whether the UMP form displays and that the test ad gate reacts
   correctly to consent being granted/denied. Do not infer EEA compliance
   solely from `canRequestAds=true` or absence of UMP errors.

The `UMP_TEST_DEVICE_HASH` BuildConfig field is populated in **debug only**.
For release variants it is always an empty string, and the UMP integration
never registers a device outside debug builds. This is a QA tool, not
permission to change production consent settings or deploy to Play.
