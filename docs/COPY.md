# How Bloo words things

One page, so the voice doesn't drift between screens written months apart. Every rule here came from
fixing something real; where a rule has an example, the example is a before/after from this codebase.

## The rules

**1. A message is a sentence. It ends with a full stop.**
Not "Auto-sync enabled" next to "Download failed. Check your connection and try again." Half the app's
messages used to end with a period and half didn't, in the same build.

**2. A `Label: detail` form does not.**
`Update available: build 812` takes no full stop -- it isn't a sentence, it's a row. The same goes for
interpolated prefixes like `Car: message`.

**3. A description is a fragment, not a sentence.**
The label names the thing and the control shows its state, so the description only states the
condition:
- ✗ *alerts when a door, trunk or hood has been open for over 5 min.*
- ✓ *Open for over 5 min.*
Anything beginning "Turn on to…", "This will…", "Bloo tells you…" is paying for words it doesn't need.

**4. Say what happened and what to do. Never apologise.**
- ✓ *Download failed. Check your connection and try again.*
- ✗ *Sorry, something went wrong.*
No "Oops", no "Uh oh", no exclamation marks. A failure the user can act on is not a crisis.

**5. Concrete beats vague.**
A number, a filename, a screen name or a duration earns its space. *Couldn't open the installer. Find
Bloo.apk in Downloads.* is better than *Installation failed.*

**6. Jargon only when it is a literal step the user must follow.**
"Wireless debugging", "ADB port" and "Shizuku" stay in the watch setup instructions, because those are
the words on the screens the user is looking at. Everywhere else, plain English: the *releases page*,
not *the GitHub Releases asset*.

**7. One noun per thing.**
A **car**, never a *vehicle*. A **build** (or *build 812*) for the version. A **pebble** for a card on
the garage -- the onboarding says "That is a pebble", so that is what it is called everywhere.
**Sign in**, as two words and both verb and noun, never *login*: the button said "Sign in" and the way
back from it said "Back to login", in the same app.

**8. Sentence case. No shouting.**
Labels and titles use sentence case. If something is important, say it plainly; capitals don't make it
more important. This one is easy to break while writing instructions, because the important word feels
like it needs marking:

- ✗ *Check the watch is on the SAME Wi-Fi … The pairing port is the one NEXT TO it … (NOT the pairing port)*
- ✓ *Check the watch is on the same Wi-Fi … The pairing port is the one next to it … (not the pairing port)*

Same instructions, same emphasis, no shouting. A reader who is following steps is already paying
attention.

## Before adding a string

Read it out loud next to its neighbours on the same screen. If two rows say the same word in two ways,
pick one. If a description explains what the label already says, delete the description.
