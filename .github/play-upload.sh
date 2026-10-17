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
  local track="$1" form_factor body e out
  case "$track" in
    wear:*)       form_factor="WEAR" ;;
    automotive:*) form_factor="AUTOMOTIVE" ;;
    *)            form_factor="DEFAULT" ;;
  esac
  body="$(jq -c -n --arg t "$track" --arg ff "$form_factor" \
    '{track: $t, type: "CLOSED_TESTING", formFactor: $ff}')"
  # ITS OWN EDIT, COMMITTED, before the release is written anywhere. That is the whole point: creating
  # a track and committing a release onto it in the SAME edit is a 500 from Play -- "Internal error
  # encountered.", no validation message -- which is what wear:alpha hit while wear:internal (made by
  # Play when the form factor was configured) committed fine. A custom closed-testing track has no
  # wear counterpart until something asks for one, and this is that ask.
  e="$(call POST "$api/edits" | jq -r .id)"
  if out="$(call POST "$api/edits/$e/tracks" -d "$body" 2>&1)"; then
    echo "  '$track' did not exist: created it (formFactor=$form_factor) and committed that first" >&2
    call POST "$api/edits/$e:commit" >/dev/null 2>&1 || true
  else
    # The expected case on every run after the first, so it is a note rather than an error -- but it
    # still says what Play said, because "already exists" and "refused" are different things.
    echo "  '$track' was not created; Play said: $(printf '%s' "$out" | grep -o '"message":[^,]*' | head -1)" >&2
  fi
  call DELETE "$api/edits/$e" >/dev/null 2>&1 || true
}

# assign one release, with its own version codes, to one track
assign_to_edit() {
  local edit_id="$1" track="$2"; shift 2
  local codes_json rel payload out
  codes_json="$(printf '%s\n' "$@" | jq -R . | jq -s .)"
  # Patch notes, when the workflow has generated them. Play takes them PER LANGUAGE on the release, and
  # they are the one thing GitHub gets and Play never did: the release object here carried only a status
  # and version codes, so every Play revision shipped with an empty "What's new" while the GitHub release
  # for the same build carried the changelog. Same text, same build number, both stores.
  local notes_json="[]"
  if [ -n "${PLAY_NOTES_FILE:-}" ] && [ -f "$PLAY_NOTES_FILE" ]; then
    # Play caps this at 500 characters; trimmed here rather than refused by the API.
    notes_json="$(jq -c -n --rawfile t "$PLAY_NOTES_FILE" '[{language: "en-US", text: ($t | .[0:480])}]')"
  fi
  rel="$(jq -c -n --arg status "${ASSIGN_STATUS:-$STATUS}" --argjson codes "$codes_json" --argjson notes "$notes_json" \
    '{status: $status, versionCodes: $codes, releaseNotes: $notes}')"
  payload="$(jq -c -n --arg track "$track" --argjson rel "$rel" '{track: $track, releases: [$rel]}')"
  echo; echo "--- $track <- $(printf '%s,' "$@" | sed 's/,$//')"

  if ! out="$(call PUT "$api/edits/$edit_id/tracks/$track" -d "$payload")"; then
    # No attempt to CREATE the track here, deliberately: creating one means committing another edit,
    # and committing any edit invalidates this one -- so the retry would answer "This Edit has been
    # deleted." The caller ensures its form-factor tracks exist BEFORE opening the edit it writes
    # releases into (see the watch section).
    echo "::error::could not assign to '$track'"
    return 1
  fi

  printf '%s' "$out" | jq -r '.releases[]? | "  release: status=\(.status) versionCodes=\((.versionCodes // [])|join(","))"' >&2 || true
}

for track in "${phone_tracks[@]}"; do
  # BOTH FORM FACTORS IN ONE RELEASE. That is the documented shape: a watch build's version code "must
  # be unique across all form factors", which only means anything if one release holds them. The 500
  # that made me split them happened BEFORE the Wear OS form factor was configured -- exactly what Play
  # answers when it is handed a watch bundle it has no form factor for -- and I routed around it
  # permanently instead of retrying it once the form factor existed. Which is why the watch was live on
  # its own track and still never offered next to the phone: a companion is associated with its phone
  # app by being in the same release.
  assign_to_edit "$edit" "$track" "${phone_codes[@]}" "${wear_codes[@]}"
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
# The read-back, with a few tries. A transient 503 HERE used to fail the whole run AFTER the release
# had committed -- so the watch step, which runs next, never ran at all. That is why wear:alpha sat at
# 10003052 for several builds while the phone on the same track was current: the phone kept committing,
# the read-back kept hitting a 503, and the run kept dying before the watch.
read_track_in() {
  local e="$1" t="$2" out i
  for i in 1 2 3; do
    if out="$(call GET "$api/edits/$e/tracks/$t" 2>/dev/null)"; then
      printf '%s' "$out" | jq -r '[(.releases // [])[] | (.versionCodes // [])[]] | join(",")'
      return 0
    fi
    [ "$i" -lt 3 ] && sleep 2
  done
  echo "::warning::could not read back '$t' -- Play returned an error three times" >&2
  return 1
}
read_track() { read_track_in "$verify_edit" "$1"; }
ok=1
for track in "${phone_tracks[@]}"; do
  seen="$(read_track "$track")"
  echo "  $track now holds: ${seen:-<nothing>}"
  for c in "${phone_codes[@]}" "${wear_codes[@]}"; do
    case ",$seen," in *",$c,"*) ;; *) echo "::error::$track does NOT hold $c -- the commit did not take"; ok=0 ;; esac
  done
done
[ "$ok" = "1" ] || exit 1
echo "PHONE PUBLISHED: $PKG -> $(printf '%s,' "${phone_tracks[@]}" | sed 's/,$//') holds $(printf '%s,' "${phone_codes[@]}" | sed 's/,$//')"

# The watch rides in the SAME release as the phone, above -- see the note on that assignment. There is
# no separate watch edit and no wear:<track> write any more: a companion is associated with its phone app
# by sharing its release, and a release on a form-factor track of its own is what a STANDALONE watch app
# gets, which this is not.
#
# Everything below is what remains when Play refuses the combined release, so it says which refusal it
# was rather than leaving the phone's success looking like the watch's.
if [ "${#wear_codes[@]}" -gt 0 ]; then
  echo
  echo "WATCH: in the same release as the phone, above (version code $(printf '%s,' "${wear_codes[@]}" | sed 's/,$//'))."
fi
