#!/bin/bash
# render every */*/{onboarding,status,apps,log}.html to PNG at 2x
cd /workspace/home-quiet-designs
for f in */*/*.html; do
  out="${f%.html}.png"
  google-chrome --headless=new --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=2 --window-size=390,844 --screenshot="$PWD/$out" "file://$PWD/$f" >/dev/null 2>&1
done
