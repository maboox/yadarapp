#!/bin/sh
# Copies the Yadimo engine from docs/yadimo (where it is designed and previewed) into the app.
# costumes.js (character-inspired test parts) goes only to the test builds' assets, never to the store build.
set -e
cd "$(dirname "$0")/.."
main=app/src/main/assets/yadimo; test=app/src/testparts/assets/yadimo
rm -rf "$main" "$test"; mkdir -p "$main" "$test"
for f in art.js volume.js acc.js warp.js engine.js bridge.js yadimo.html; do cp "docs/yadimo/$f" "$main/"; done
cp docs/yadimo/costumes.js "$test/"
echo "synced"
