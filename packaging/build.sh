#!/usr/bin/env bash
# Builds the Windows and macOS packages of Kai (run on a Mac). One command, from anywhere:
#   packaging/build.sh
# It runs ./mvnw clean package at the project root, then packages target/kai-*.jar.
# One-time setup: put a Java 21 runtime for each target in packaging/jre/ (or set JRE_DIR):
#   winjre/   Windows x64 runtime, must contain bin/java.exe
#   macjre/   macOS runtime, must contain bin/java
# Shipped with each package: kai.properties.template (as kai.properties) and packaging/README.md.
# Output: packaging/dist/{windows,macos}/ with kai(.exe), kai.properties, README,
#         plus kai-windows.zip and kai-macos.zip (the zip keeps the Mac exec bit).
# MAC_ARCH=amd64 for an Intel macjre (default arm64)
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
jre=${JRE_DIR:-$here/jre}
props="$root/kai.properties.template"
readme="$here/README.md"
out="$here/dist"
mac_arch=${MAC_ARCH:-arm64}

fail() { echo "$*" >&2; exit 1; }
command -v go >/dev/null || fail "Go is not installed (brew install go)"
for f in winjre/bin/java.exe macjre/bin/java; do
  [[ -e $jre/$f ]] || fail "Missing $jre/$f (unzip the Temurin 21 JREs there once)"
done
[[ -e $props && -e $readme ]] || fail "Missing $props or $readme"

(cd "$root" && ./mvnw -q clean package)
jars=("$root"/target/kai-*.jar) # the .jar.original file does not match
[[ ${#jars[@]} -eq 1 && -e ${jars[0]} ]] || fail "Expected exactly one target/kai-*.jar"
jar=${jars[0]}

# build <goos> <goarch> <jre folder> <program name> <package name>
build() {
  local stage dir="$out/$5"
  stage=$(mktemp -d)
  cp -R "$jre/$3" "$stage/jre"
  cp "$jar" "$stage/kai.jar"
  # -h: store symlink targets as plain files; COPYFILE_DISABLE: no macOS ._ files
  COPYFILE_DISABLE=1 tar -czhf "$here/launcher/payload.tar.gz" -C "$stage" jre kai.jar
  rm -rf "$stage"
  mkdir -p "$dir"
  (cd "$here/launcher" && GOOS=$1 GOARCH=$2 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$dir/$4" .)
  cp "$props" "$dir/kai.properties"
  cp "$readme" "$dir/"
  (cd "$out" && zip -qr "kai-$5.zip" "$5")
  echo "Built $dir"
}

rm -rf "$out"
build windows amd64 winjre kai.exe windows
build darwin "$mac_arch" macjre kai macos
rm "$here/launcher/payload.tar.gz"
