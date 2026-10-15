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
    echo "::warning::HTTP $status from $method $url" >&2
    echo "$body" >&2
    # RETURN, not exit. `call` is used as `call ... || handler` for the steps that are allowed to fail,
    # and `exit` inside a function tears the whole script down before that handler can run. That is why
    # a best-effort cleanup reported itself as a fatal error: the `|| true` never got control.
    return 1
  fi
  printf '%s' "$body"
}

echo; echo "--- opening an edit"
edit_out="$(call POST "$api/edits")" || { echo "::error::could not open an edit"; echo "$edit_out"; exit 1; }
edit="$(printf '%s' "$edit_out" | jq -r .id)"
[ -n "$edit" ] && [ "$edit" != "null" ] || { echo "::error::no edit id in: $edit_out"; exit 1; }
echo "edit $edit"

# What Play already has, before anything is written. A form-factor track -- wear:internal, and so on --
# is NOT created by uploading a bundle. It appears only once something is written to it, and a PUT to
# one Play has never seen is a flat "Track not found: wear:internal". That is the 404 the watch kept
# hitting, and it is why nothing has ever reached a watch track.
echo "tracks Play already has:"
call GET "$api/edits/$edit/tracks" | jq -r '.tracks[]? | "  \(.track)"' || true

# One bundle upload per file, collecting the version code Play assigns to each.
# Per FORM FACTOR, because those are separate tracks: Play prefixes form-factor track ids, e.g.
# "wear:production". That prefix is the whole answer -- one release per track, one track per form
# factor. The phone bundle is "app-play-release.aab", the watch one "wear-play-release.aab", and the
# filenames are what say which is which.
phone_codes=(); wear_codes=()
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
  case "$(basename "$f")" in
    wear-*) wear_codes+=("$code") ;;
    *)      phone_codes+=("$code") ;;
  esac
done
# One release, one version code. That is the only shape the API accepts: a release holding two form
# factors 500s with "Internal error encountered", and two releases on one track 400s with "Only one
# completed release is allowed". So the phone is released on <track> below, and the watch gets its own
# edit and its own form-factor track, where a refusal cannot touch the phone.

IFS=',' read -ra LIST <<< "$TRACKS"

# The phone goes first, in its own edit, and its commit is the one that must succeed. Everything the
# user has asked for on Play is the phone, so a watch problem must not be able to take it down -- which
# is exactly what happened for the last six runs.
phone_tracks=()
for t in "${LIST[@]}"; do phone_tracks+=("$(echo "$t" | xargs)"); done

# Create a form-factor track that does not exist yet. Play does not make one just because a bundle of
# that form factor was uploaded: a write is what brings it into being, and the first write is refused
# with "Track not found: wear:alpha". So the order has to be create, then write.
#
# Creating takes a TrackConfig, NOT a Track, per Play's own discovery document:
#
#   track       Required. Identifier of the new track.
#   type        Required. TRACK_TYPE_UNSPECIFIED | CLOSED_TESTING
#   formFactor  Required. FORM_FACTOR_UNSPECIFIED | DEFAULT | WEAR | AUTOMOTIVE
#
# `type` documents "the only supported value is closedTesting". And when the form factor is not set up,
# Play answers with the exact remedy rather than a code:
#
#   400 FAILED_PRECONDITION
#   "Form factor 'wear' is not configured. Configure this form factor in 'Advanced settings' page on
#    Play Console."
#
# That is a one-time console setting and there is no API for it, so it is left to Play to say. Once the
# form factor is configured, this call is what creates the track and the watch publishes with nothing
# further.
ensure_track() {
  local edit_id="$1" track="$2" form_factor body
  case "$track" in
    wear:*)       form_factor="WEAR" ;;
    automotive:*) form_factor="AUTOMOTIVE" ;;
    *)            form_factor="DEFAULT" ;;
  esac
  echo "  '$track' does not exist yet, so creating it (formFactor=$form_factor, CLOSED_TESTING)" >&2
  body="$(jq -c -n --arg t "$track" --arg ff "$form_factor" \
    '{track: $t, type: "CLOSED_TESTING", formFactor: $ff}')"
  call POST "$api/edits/$edit_id/tracks" -d "$body" >/dev/null
}

# assign one release, with its own version codes, to one track
assign_to_edit() {
  local edit_id="$1" track="$2"; shift 2
  local codes_json rel payload out
  codes_json="$(printf '%s\n' "$@" | jq -R . | jq -s .)"
  rel="$(jq -c -n --arg status "$STATUS" --argjson codes "$codes_json" '{status: $status, versionCodes: $codes}')"
  payload="$(jq -c -n --arg track "$track" --argjson rel "$rel" '{track: $track, releases: [$rel]}')"
  echo; echo "--- $track <- $(printf '%s,' "$@" | sed 's/,$//')"

  if ! out="$(call PUT "$api/edits/$edit_id/tracks/$track" -d "$payload")"; then
    if [[ "$track" != *:* ]]; then
      echo "::error::could not assign to '$track'"
      return 1
    fi
    # Only a form-factor track can be missing. Bring it into being if that is possible at all, then
    # write once more -- the retry happens regardless, because the form factor may have been enabled by
    # something else since the last run.
    ensure_track "$edit_id" "$track" || true
    out="$(call PUT "$api/edits/$edit_id/tracks/$track" -d "$payload")" || {
      echo "::error::could not assign to '$track'"
      return 1
    }
  fi

  printf '%s' "$out" | jq -r '.releases[]? | "  release: status=\(.status) versionCodes=\((.versionCodes // [])|join(","))"' >&2 || true
}

for track in "${phone_tracks[@]}"; do
  assign_to_edit "$edit" "$track" "${phone_codes[@]}"
done

echo; echo "--- committing the phone release"
commit_out="$(call POST "$api/edits/$edit:commit")" || {
  echo "::error::the phone commit failed, so NOTHING was published"
  [ -n "${commit_out:-}" ] && echo "$commit_out" >&2
  exit 1
}
echo "  commit response: ${commit_out:-<empty>}"

# Read it back. "Committed" is a claim; this is the check. A fresh edit only ever sees the last
# committed state.
echo; echo "--- verifying the phone against a fresh edit"
verify_edit="$(call POST "$api/edits" | jq -r .id)"
read_track() { call GET "$api/edits/$verify_edit/tracks/$1" | jq -r '[(.releases // [])[] | (.versionCodes // [])[]] | join(",")'; }
ok=1
for track in "${phone_tracks[@]}"; do
  seen="$(read_track "$track")"
  echo "  $track now holds: ${seen:-<nothing>}"
  for c in "${phone_codes[@]}"; do
    case ",$seen," in *",$c,"*) ;; *) echo "::error::$track does NOT hold $c -- the commit did not take"; ok=0 ;; esac
  done
done
[ "$ok" = "1" ] || exit 1
echo "PHONE PUBLISHED: $PKG -> $(printf '%s,' "${phone_tracks[@]}" | sed 's/,$//') holds $(printf '%s,' "${phone_codes[@]}" | sed 's/,$//')"

# ---------------------------------------------------------------------------------------------------
# The watch, in a SECOND edit, deliberately: its own commit, so a refused form-factor track cannot
# undo the phone's. Play would roll the whole thing back if this shared an edit, and the phone is what
# everyone actually asked for.
if [ "${#wear_codes[@]}" -eq 0 ]; then
  echo; echo "no watch bundle in this run"
  call DELETE "$api/edits/$verify_edit" >/dev/null || true
  exit 0
fi

echo; echo "=== the watch, in its own edit ==="
watch_edit="$(call POST "$api/edits" | jq -r .id)"
watch_ok=1
for track in "${phone_tracks[@]}"; do
  assign_to_edit "$watch_edit" "wear:$track" "${wear_codes[@]}" || { watch_ok=0; continue; }
done
if [ "$watch_ok" = "1" ]; then
  echo; echo "--- committing the watch release"
  if wout="$(call POST "$api/edits/$watch_edit:commit")"; then
    echo "  commit response: ${wout:-<empty>}"
    for track in "${phone_tracks[@]}"; do
      seen="$(read_track "wear:$track")"
      echo "  wear:$track now holds: ${seen:-<nothing>}"
      for c in "${wear_codes[@]}"; do
        case ",$seen," in *",$c,"*) ;; *) echo "::warning::wear:$track does not hold $c"; watch_ok=0 ;; esac
      done
    done
  else
    echo "::warning::the watch commit was refused -- the phone is published, the watch is not"
    watch_ok=0
  fi
else
  call DELETE "$api/edits/$watch_edit" >/dev/null || true
fi
call DELETE "$api/edits/$verify_edit" >/dev/null || true

if [ "$watch_ok" = "1" ]; then
  echo "WATCH PUBLISHED: $PKG -> wear:<track> holds $(printf '%s,' "${wear_codes[@]}" | sed 's/,$//')"
else
  echo "::warning::the watch did not publish. The phone release is unaffected."
  echo "  Its bundle is uploaded and sits in the store's bundle library, so nothing is lost."
  echo "  If Play's reason above is 'Form factor is not configured': set it once at Play Console ->"
  echo "  Advanced settings -> form factors. There is no API for that setting, which is the only reason"
  echo "  this step cannot do it. After it is set, this step publishes the watch on every run with"
  echo "  nothing further to do."
fi

# What the tracks look like at the end. If a wear: track shows up here that was not there at the start,
# the form factor has been enabled, and the next run publishes the watch with no further involvement.
echo; echo "--- tracks now"
final_edit="$(call POST "$api/edits" | jq -r .id)"
call GET "$api/edits/$final_edit/tracks" | jq -r '.tracks[]? | "  \(.track)"' || true
call DELETE "$api/edits/$final_edit" >/dev/null || true
