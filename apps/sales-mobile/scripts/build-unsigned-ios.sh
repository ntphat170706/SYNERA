#!/usr/bin/env bash
set -euo pipefail

workspace=$(find ios -maxdepth 1 -name '*.xcworkspace' -print -quit)
test -n "$workspace"
scheme=$(basename "$workspace" .xcworkspace)
mkdir -p output
xcodebuild \
  -workspace "$workspace" \
  -scheme "$scheme" \
  -configuration Release \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -archivePath "$PWD/output/SYNERA.xcarchive" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY= \
  archive

app=$(find output/SYNERA.xcarchive/Products/Applications -maxdepth 1 -name '*.app' -print -quit)
test -n "$app"
test -f "$app/main.jsbundle"
platform=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleSupportedPlatforms:0' "$app/Info.plist")
test "$platform" = iPhoneOS
executable=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app/Info.plist")
xcrun lipo -verify_arch arm64 "$app/$executable"
mkdir -p output/package/Payload
ditto "$app" "output/package/Payload/$(basename "$app")"
(
  cd output/package
  /usr/bin/zip -q -r -y ../SYNERA-Sales-unsigned.ipa Payload
)
/usr/bin/unzip -tq output/SYNERA-Sales-unsigned.ipa
shasum -a 256 output/SYNERA-Sales-unsigned.ipa
