#!/usr/bin/env bash
#
# Release: 1.0.0-SNAPSHOT -> 1.0.0 -> deploy -> tag v1.0.0 -> 1.0.1-SNAPSHOT.
#
#   ./release.sh            asks before publishing, leaves pushing to you
#   ./release.sh -y         no question
#   ./release.sh --push     also pushes the branch and the tag (origin)
#
# Steps (each Maven call is a separate JVM, because versions:set rewrites pom.xml on disk and a
# running build would keep the old in-memory version):
#   1. versions:set -DremoveSnapshot            + commit "Release X"
#   2. mvn clean deploy -Pdeployment            builds, signs and publishes to Central
#   3. git tag -a vX
#   4. versions:set -DnextSnapshot (PATCH + 1)  + commit "Prepare next development iteration"
#
# Needs: a clean working tree with at least one commit, gpg.keyname (settings.xml or -D) and the
# `central` server in settings.xml. The GPG passphrase is read from MAVEN_GPG_PASSPHRASE if set,
# otherwise the gpg plugin asks for it. The tag is created only after a successful deploy.
set -euo pipefail
cd "$(dirname "$0")"

ASSUME_YES=false
PUSH=false
for arg in "$@"; do
    case "$arg" in
        -y|--yes) ASSUME_YES=true ;;
        --push) PUSH=true ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

die() { echo "release: $*" >&2; exit 1; }

git rev-parse --verify HEAD >/dev/null 2>&1 || die "no commits yet, commit the current state first"
[ -z "$(git status --porcelain)" ] || die "working tree is not clean"

CURRENT=$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout)
case "$CURRENT" in
    *-SNAPSHOT) ;;
    *) die "version $CURRENT is not a -SNAPSHOT" ;;
esac
RELEASE=${CURRENT%-SNAPSHOT}
TAG="v$RELEASE"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"

echo "Release $RELEASE (tag $TAG), then next development version (PATCH + 1)."
if [ "$ASSUME_YES" != true ]; then
    read -r -p "Publish to Maven Central? [y/N] " answer
    [ "$answer" = y ] || [ "$answer" = Y ] || die "cancelled"
fi

mvn -B -q versions:set -DremoveSnapshot=true
git commit -m "Release $RELEASE" -- pom.xml

if ! mvn clean deploy -Pdeployment; then
    echo "release: deploy failed; pom.xml still says $RELEASE in the commit 'Release $RELEASE'." >&2
    echo "         Fix the cause and run 'mvn clean deploy -Pdeployment', or undo with 'git reset --hard HEAD~1'." >&2
    exit 1
fi

git tag -a "$TAG" -m "Release $RELEASE"

mvn -B -q versions:set -DnextSnapshot=true -DnextSnapshotIndexToIncrement=3
NEXT=$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout)
git commit -m "Prepare next development iteration $NEXT" -- pom.xml

if [ "$PUSH" = true ]; then
    git push origin HEAD "$TAG"
else
    echo "Done. Publish the tag and the commits with: git push origin HEAD $TAG"
fi
