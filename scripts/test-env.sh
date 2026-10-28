#!/usr/bin/env bash
# Builds a throw-away copy of the golden sample docs for TEST-PLAN.md, and removes it again.
#
#   scripts/test-env.sh setup   fresh copy (replaces any earlier one) + a test config
#   scripts/test-env.sh clean   delete the copy, the history folder and the config
#   scripts/test-env.sh reset   clean + setup: back to the golden state between tests
#   scripts/test-env.sh diff    what Kai changed since setup (copy vs golden)
#
# Golden docs: sampleDocs/ (tests only ever touch the copy, never these).
# Test copy:   $KAI_TEST_DIR, default /tmp/kai-test
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GOLDEN="$ROOT/sampleDocs"
WORK="${KAI_TEST_DIR:-/tmp/kai-test}"
MARK="$WORK/.kai-test-env" # clean only deletes a folder that setup made

clean() {
	if [ -e "$WORK" ] && [ ! -f "$MARK" ]; then
		echo "Refusing to delete $WORK: it was not made by this script." >&2
		exit 1
	fi
	rm -rf "$WORK"
	echo "Removed $WORK"
}

setup() {
	[ -d "$GOLDEN" ] || { echo "No golden docs at $GOLDEN" >&2; exit 1; }
	clean
	mkdir -p "$WORK/history"
	touch "$MARK"
	for d in "$GOLDEN"/*/; do
		cp -R "$d" "$WORK/$(basename "$d")"
	done
	find "$WORK" -name .DS_Store -delete
	chmod -R u+w "$WORK"

	# Test config: the template with the three kai.* settings pointed at the copy
	folders=$(find "$WORK" -mindepth 1 -maxdepth 1 -type d ! -name history | sort | paste -sd, - | sed 's/,/, /g')
	sed -e "s|^kai.scan.local=.*|kai.scan.local=$folders|" \
		-e "s|^kai.backup-dir=.*|kai.backup-dir=$WORK/history|" \
		-e "s|^kai.scan.parallel=.*|kai.scan.parallel=1|" \
		"$ROOT/kai.properties.template" > "$WORK/kai.properties"

	echo "Test copy ready in $WORK"
	echo "Start Kai with:  --config=$WORK/kai.properties"
}

case "${1:-}" in
	setup) setup ;;
	clean) clean ;;
	reset) setup ;; # setup starts with clean
	diff) diff -r "$GOLDEN" "$WORK" -x history -x kai.properties -x .kai-test-env -x .DS_Store && echo "No differences from golden." ;;
	*) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
