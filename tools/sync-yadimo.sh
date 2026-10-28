#!/bin/sh
# Copies the Yadimo engine from docs/yadimo (where it is designed and previewed) into the app.
set -e
cd "$(dirname "$0")/.."
main=app/src/main/assets/yadimo
rm -rf "$main"; mkdir -p "$main"
for f in art.js volume.js acc.js costumes.js warp.js engine.js bridge.js yadimo.html roomscene.js roomframes.js roomframes.html roomlive.js room.html; do cp "docs/yadimo/$f" "$main/"; done
echo "synced"
