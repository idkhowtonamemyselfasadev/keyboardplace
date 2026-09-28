#!/usr/bin/env bash
# Build Keyboard Place and install it into the "Better PvP" Modrinth App profile.
#
# Safe to run while the game is running. It never writes over a path in place -
# see the note by the rename below - but the new jar only takes effect on the
# next launch, because Fabric reads the mods folder once at startup.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROFILE_DIR="$HOME/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/Better PvP"
MODS_DIR="$PROFILE_DIR/mods"
GRADLE="$HOME/.gradle/wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764/gradle-9.6.1/bin/gradle"

[ -d "$MODS_DIR" ] || { echo "No mods folder at $MODS_DIR" >&2; exit 1; }

"$GRADLE" -p "$PROJECT_DIR" build

JAR="$(ls -t "$PROJECT_DIR"/build/libs/keyboardplace-*.jar | grep -v -- '-sources' | head -1)"
[ -n "$JAR" ] || { echo "No jar was built" >&2; exit 1; }

NAME="$(basename "$JAR")"
GAME_PID="$(pgrep -f 'net.minecraft.client.main.Main' | head -1 || true)"

# Install by rename, never by writing over a path in place.
#
# "cp onto an existing jar" is what corrupts a running game: cp truncates the
# file the JVM has open, and the next class it loads dies with
# "ZipException: invalid LOC header". A rename replaces the directory entry
# instead, so a running JVM keeps reading the old inode quite happily, and
# unlinking a still-open file is equally harmless on Linux.
STAGED="$MODS_DIR/.${NAME}.staging"
cp "$JAR" "$STAGED"

# Glob, not word-splitting: the profile directory is called "Better PvP" and the
# space in it silently broke an earlier version of this loop.
shopt -s nullglob
for old in "$MODS_DIR"/keyboardplace-*.jar; do
    if [ "$old" != "$MODS_DIR/$NAME" ]; then
        rm -f "$old"
        echo "Removed stale $(basename "$old")"
    fi
done
shopt -u nullglob

mv -f "$STAGED" "$MODS_DIR/$NAME"

echo "Installed $NAME -> $MODS_DIR"

# Exactly one jar of ours must remain, or Fabric refuses to start on duplicate ids.
COUNT="$(find "$MODS_DIR" -maxdepth 1 -name 'keyboardplace-*.jar' | wc -l)"
if [ "$COUNT" -ne 1 ]; then
    echo "Expected exactly one keyboardplace jar in mods, found $COUNT" >&2
    exit 1
fi

if [ -n "$GAME_PID" ]; then
    echo "Minecraft is running (pid $GAME_PID); it loads this on the next launch."
fi
exit 0
