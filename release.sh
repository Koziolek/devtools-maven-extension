#!/usr/bin/env bash
#
# Release: 1.0.0-SNAPSHOT -> 1.0.0 -> deploy -> tag v1.0.0 -> 1.0.1-SNAPSHOT.
#
#   ./release.sh                   asks before publishing, leaves pushing to you
#   ./release.sh -y                no question
#   ./release.sh --push            also pushes the branch and the tag (origin)
#   ./release.sh --check-published resume after a deploy that failed on the Maven side (see below)
#
# Steps (each Maven call is a separate JVM, because versions:set rewrites pom.xml on disk and a
# running build would keep the old in-memory version):
#   1. versions:set -DremoveSnapshot            + commit "Release X"
#   2. mvn clean deploy -Pdeployment            builds, signs, uploads to Central and waits
#                                               until Central reports PUBLISHED (up to 30 min)
#   3. git tag -a vX
#   4. versions:set -DnextSnapshot (PATCH + 1)  + commit "Prepare next development iteration"
#
# Needs: a clean working tree with at least one commit, gpg.keyname (settings.xml or -D) and the
# `central` server in settings.xml. The GPG passphrase is read from MAVEN_GPG_PASSPHRASE if set,
# otherwise the gpg plugin asks for it. The tag is created only after Central has validated and
# published the deployment; a rejected deployment fails the build and leaves no tag.
#
# --check-published: for a run where step 2 failed although Central did publish (e.g. the plugin
# could not read the status response). It skips steps 1 and 2 and works on the version that pom.xml
# holds now, which must be a release version (the "Release X" commit is HEAD, not a -SNAPSHOT).
# Only if that version is already on Maven Central (its .pom is served by repo1.maven.org) it does
# steps 3 and 4; otherwise it stops and changes nothing. Central needs a few minutes after
# PUBLISHED before repo1.maven.org serves the file, so run it again if the check fails too early.
set -euo pipefail
cd "$(dirname "$0")"

ASSUME_YES=false
PUSH=false
CHECK_PUBLISHED=false
for arg in "$@"; do
    case "$arg" in
        -y|--yes) ASSUME_YES=true ;;
        --push) PUSH=true ;;
        --check-published) CHECK_PUBLISHED=true ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

die() { echo "release: $*" >&2; exit 1; }

pom_value() { mvn -q -N help:evaluate -Dexpression="$1" -DforceStdout; }

# True when Maven Central already serves the .pom of $1 (a version of this project).
is_published() {
    local group artifact url
    group=$(pom_value project.groupId)
    artifact=$(pom_value project.artifactId)
    url="https://repo1.maven.org/maven2/${group//./\/}/$artifact/$1/$artifact-$1.pom"
    echo "Checking $url"
    curl -fsI --max-time 30 -o /dev/null "$url"
}

git rev-parse --verify HEAD >/dev/null 2>&1 || die "no commits yet, commit the current state first"
TAG_COMMIT=HEAD
if [ "$CHECK_PUBLISHED" = true ]; then
    # TEMPORARY (remove once pom.xml is on the next -SNAPSHOT): other files may be modified, e.g. this
    # script, so committing them does not move the tag; only pom.xml must be committed because the
    # version bump below commits it.
    git diff --quiet HEAD -- pom.xml || die "pom.xml has uncommitted changes"
else
    [ -z "$(git status --porcelain)" ] || die "working tree is not clean"
fi

CURRENT=$(pom_value project.version)

if [ "$CHECK_PUBLISHED" = true ]; then
    case "$CURRENT" in
        *-SNAPSHOT) die "version $CURRENT is a -SNAPSHOT; there is no release to resume, run ./release.sh" ;;
    esac
    RELEASE=$CURRENT
    TAG="v$RELEASE"
    git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"
    is_published "$RELEASE" \
        || die "$RELEASE is not on Maven Central yet (status PUBLISHED can precede repo1.maven.org by a few minutes); nothing changed"
    # TEMPORARY (remove together with the relaxed check above): tag the "Release X" commit itself, not
    # HEAD, which may be a later commit; the tag has to match what was uploaded to Central.
    TAG_COMMIT=$(git log -n 1 --format=%H --grep="^Release $RELEASE\$")
    [ -n "$TAG_COMMIT" ] || die "no commit with the subject 'Release $RELEASE' found"
    echo "Release $RELEASE is on Maven Central; tagging $TAG on ${TAG_COMMIT:0:7} and moving to the next development version."
else
    case "$CURRENT" in
        *-SNAPSHOT) ;;
        *) die "version $CURRENT is not a -SNAPSHOT (after a failed deploy use --check-published)" ;;
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
        echo "release: deploy failed; the commit 'Release $RELEASE' is still there." >&2
        echo "         Check https://central.sonatype.com/publishing. If the deployment is PUBLISHED" >&2
        echo "         (the Maven plugin may only have failed to read the status), run" >&2
        echo "         './release.sh --check-published' to tag and bump the version." >&2
        echo "         If Central rejected it: drop the deployment there, fix the cause and run" >&2
        echo "         'git reset --hard HEAD~1' to undo the release commit." >&2
        exit 1
    fi
fi

git tag -a "$TAG" -m "Release $RELEASE" "$TAG_COMMIT"

mvn -B -q versions:set -DnextSnapshot=true -DnextSnapshotIndexToIncrement=3
NEXT=$(pom_value project.version)
git commit -m "Prepare next development iteration $NEXT" -- pom.xml

if [ "$PUSH" = true ]; then
    git push origin HEAD "$TAG"
else
    echo "Done. Publish the tag and the commits with: git push origin HEAD $TAG"
fi
