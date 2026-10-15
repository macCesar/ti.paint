# Android release regression test

Build the module, then run the test with one Android 10+ device connected via ADB:

```sh
cd android
ti build -p android --build-only --sdk 13.4.1.GA --no-prompt
cd ..
python3 tests/android/run.py
```

Requires the Titanium CLI, JDK and Android SDK platform/build tools 36. Override the Android SDK path with `--android-sdk` and the Titanium version with `--sdk`. Use `--module-zip` to test another packaged build.

The runner creates a temporary Titanium application, signs it and the instrumentation APK with a temporary key, and installs the two test packages (`ti.paint.releasefixture` and `ti.paint.tests`). It replaces previous installations of those test packages. Build logs and results remain in the printed temporary directory.

The test exercises the real module and Titanium lifecycle on the device. It checks release while idle, playing and paused, cancellation of scheduled playback, clearing drawing/undo/playback data, calls and messages after release, late layout/draw callbacks, repeated release and creation of a fresh native view. The proxy and both old view objects are deliberately retained while checking resource cleanup.

Before the fix, this test fails with `released proxy retains UIPaintView`. These deterministic lifecycle assertions do not replace a LeakCanary heap analysis of an application.
