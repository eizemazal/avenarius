# Avenarius builds. More dev commands (tests, lint, logs) live in ./dev.sh.

apk_dir := "composeApp/build/outputs/apk"

default:
    @just --list

# Debug APK (com.avenarius.app.dev, installs alongside the Play release)
debug:
    ./gradlew :composeApp:assembleDebug
    @echo "APK: {{apk_dir}}/debug/composeApp-debug.apk"

# Release APK (R8); signed only when AVENARIUS_KEYSTORE* env vars are set
release:
    ./gradlew :composeApp:assembleRelease
    @ls {{apk_dir}}/release/*.apk
