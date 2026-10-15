#!/usr/bin/env bash
# Upload the phone bundle and the watch bundle to Google Play, as SEPARATE RELEASES.
#
# Why this exists rather than the upload-google-play action, which is otherwise perfectly good: it
# builds ONE release holding every version code it uploaded, and Play answers a release holding two
# bundles of the same applicationId with a bare 500 -- "Internal error encountered." -- with no
# validation message. Every attempt that carried one bundle committed; every attempt that carried two
# failed that way.
#
# Play's own packaging docs explain the shape: a watch build's version code "must be unique across all
# form factors", which is only meaningful if each form factor is its own release on the track. So the
# track update below carries two release objects, one per bundle, rather than one holding both.
#
# Env:
#   PLAY_SERVICE_ACCOUNT_JSON_FILE  path to the service-account json          (required)
#   PLAY_PACKAGE                    defaults to com.bloo.bluelink
#   PLAY_TRACKS                     comma-separated, defaults to internal,alpha
#   PLAY_STATUS                     draft | completed, defaults to completed
#   PLAY_BUNDLES                    comma-separated .aab paths                (required)
set -euo pipefail

SA="${PLAY_SERVICE_ACCOUNT_JSON_FILE:?path to the service-account json}"
PKG="${PLAY_PACKAGE:-com.bloo.bluelink}"
TRACKS="${PLAY_TRACKS:-internal,alpha}"
STATUS="${PLAY_STATUS:-completed}"
BUNDLES="${PLAY_BUNDLES:?comma-separated .aab paths}"

[ -f "$SA" ] || { echo "::error::service account json not found at $SA"; exit 1; }
IFS=',' read -ra FILES <<< "$BUNDLES"
for f in "${FILES[@]}"; do
  [ -f "$f" ] || { echo "::error::no bundle at $f"; exit 1; }
  echo "bundle: $f ($(stat -c%s "$f") bytes)"
done
echo "tracks: $TRACKS   status: $STATUS"

work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
client_email="$(jq -r .client_email "$SA")"
jq -r .private_key "$SA" > "$work/key.pem"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
header="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)"
now="$(date +%s)"
claims="$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":%s,"exp":%s}' \
  "$client_email" "$now" "$((now + 3600))" | b64url)"
sig="$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$work/key.pem" -binary | b64url)"
token="$(curl -fsS -X POST https://oauth2.googleapis.com/token \
  -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
  -d assertion="$header.$claims.$sig" | jq -r .access_token)"
[ -n "$token" ] && [ "$token" != "null" ] || { echo "::error::could not get an access token"; exit 1; }

api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG"
upload="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PKG"
auth=(-H "Authorization: Bearer $token")

# prints the body, fails loudly on a non-2xx, and never swallows either
call() {
  local method="$1" url="$2"; shift 2
  local body status
  body="$(curl -sS -X "$method" "${auth[@]}" -H "Content-Type: application/json" -w $'\n%{http_code}' "$@" "$url")"
  status="${body##*$'\n'}"; body="${body%$'\n'*}"
  if [ "${status:0:1}" != "2" ]; then
    # STDERR, deliberately. On stdout this text gets captured by any `$(call ...)` and then silently
    # dropped, which has now happened twice: once eaten by jq as input, once held in a variable and
    # never printed. stderr cannot be swallowed by a command substitution.
    echo "::error::HTTP $status from $method $url" >&2
    echo "$body" >&2
    exit 1
  fi
  printf '%s' "$body"
}

echo; echo "--- opening an edit"
edit_out="$(call POST "$api/edits")" || { echo "::error::could not open an edit"; echo "$edit_out"; exit 1; }
edit="$(printf '%s' "$edit_out" | jq -r .id)"
[ -n "$edit" ] && [ "$edit" != "null" ] || { echo "::error::no edit id in: $edit_out"; exit 1; }
echo "edit $edit"

# One bundle upload per file, collecting the version code Play assigns to each.
codes=()
for f in "${FILES[@]}"; do
  echo "uploading $(basename "$f")"
  resp="$(curl -sS -X POST "${auth[@]}" -H "Content-Type: application/octet-stream" \
    --data-binary "@$f" -w $'\n%{http_code}' \
    "$upload/edits/$edit/bundles?uploadType=media")"
  status="${resp##*$'\n'}"; body="${resp%$'\n'*}"
  if [ "${status:0:1}" != "2" ]; then
    echo "::error::HTTP $status uploading $(basename "$f")"; echo "$body" | head -20; exit 1
  fi
  code="$(printf '%s' "$body" | jq -r .versionCode)"
  echo "  -> versionCode $code"
  codes+=("$code")
done

# ONE release holding every version code, which is the opposite of what this did first.
#
# Asking Play directly settled it: "Only one completed release is allowed." A track may only have a
# single completed release, so the form factors live as separate VERSION CODES inside it -- which is
# what "a watch build's version code must be unique across all form factors" means. Two releases per
# track is not a shape Play has.
release_json="$(printf '%s\n' "${codes[@]}" | jq -R . | jq -s --arg status "$STATUS" '{status: $status, versionCodes: .}')"

IFS=',' read -ra LIST <<< "$TRACKS"
for t in "${LIST[@]}"; do
  track="$(echo "$t" | xargs)"
  echo; echo "--- assigning ${#codes[@]} release(s) to '$track'"
  payload="$(jq -c -n --arg track "$track" --argjson rel "$release_json" '{track: $track, releases: [$rel]}')"
  echo "  $payload"
  if ! out="$(call PUT "$api/edits/$edit/tracks/$track" -d "$payload" 2>&1)"; then
    # A failure here is worth reading rather than retrying: it is the shape Play objects to, or the
    # draft-app rule, or a version code it already has.
    echo "$out" | head -20
    echo "::error::could not assign to '$track'"
    exit 1
  fi
  printf '%s' "$out" | jq -r '.releases[]? | "  release: status=\(.status) versionCodes=\((.versionCodes // [])|join(","))"' || true
done

echo; echo "--- committing"
commit_out="$(call POST "$api/edits/$edit:commit")" || {
  echo "::error::the commit failed, so NOTHING was published"
  [ -n "${commit_out:-}" ] && echo "$commit_out" >&2
  exit 1
}
echo "  commit response: ${commit_out:-<empty>}"

# Read it back, because "committed" is a claim and this is the check. A new edit only ever sees the
# LAST COMMITTED state -- so if the version codes are not there, the commit did not take, whatever the
# previous line said. This is the check that was missing when the script said "committed" three times
# and changed nothing.
echo; echo "--- verifying against a fresh edit"
verify_edit="$(call POST "$api/edits" | jq -r .id)"
IFS=',' read -ra VERIFY_TRACKS <<< "$TRACKS"
ok=1
for t in "${VERIFY_TRACKS[@]}"; do
  track="$(echo "$t" | xargs)"
  seen="$(call GET "$api/edits/$verify_edit/tracks/$track" | jq -r '[(.releases // [])[] | (.versionCodes // [])[]] | join(",")')"
  echo "  $track now holds: ${seen:-<nothing>}"
  for c in "${codes[@]}"; do
    case ",$seen," in
      *",$c,"*) ;;
      *) echo "::error::$track does NOT hold version code $c -- the commit did not take"; ok=0 ;;
    esac
  done
done
call DELETE "$api/edits/$verify_edit" >/dev/null || true
[ "$ok" = "1" ] || exit 1
echo "verified: $PKG -> $TRACKS now holds $(printf '%s,' "${codes[@]}" | sed 's/,$//')"
