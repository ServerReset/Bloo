
p = "app/src/main/java/com/bloo/bluelink/ui/Screens.kt"
lines = open(p, encoding="utf-8").read().split("\n")

# --- 1. Replace the expanded readout (bottom of card) with the single morphing node.
a = next(i for i,l in enumerate(lines) if l.strip() == "AnimatedVisibility(" and 5120 < i < 5140)
# find the closing "                }" of that AnimatedVisibility block, then the "            }," of background
depth = 0
for j in range(a, len(lines)):
    code = lines[j].split("//")[0]
    depth += code.count("{") - code.count("}")
    if depth == 0 and j > a:
        break
assert lines[j] == "                }", repr(lines[j])
new = """                // THE readout -- one instance for both states, morphing between them.
                //
                // Bottom-anchored and NOT wrapped in an AnimatedVisibility, because there is
                // nothing to make visible or invisible any more: this node exists in both
                // states. The travel is free -- the photo above is already animating the
                // card's height, so anchoring here rides that change from the header down to
                // the base of the photo with no bounds animation at all. `heroT` drives only
                // the SIZE morph. See HeroMorphReadout for why three earlier attempts using
                // sharedBounds had to go.
                //
                // The paddings lerp too, which is what widens the bar: collapsed it stops
                // short of the chevron, expanded it runs the full card width.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = lerp(64.dp, 16.dp, heroT),
                            bottom = lerp(10.dp, 16.dp, heroT),
                        ),
                ) {
                    HeroMorphReadout(readout, heroT)
                }"""
lines[a:j+1] = new.split("\n")
print("expanded readout -> morphing node")

# --- 2. titleTrailing and headerContent both become null: the one node covers both.
s = "\n".join(lines)
i = s.index("            titleTrailing = {")
k = s.index("            summary = null,")
s = s[:i] + """            // No titleTrailing and no headerContent. Both used to hold a collapsed COPY of
            // the readout -- a one-line ChargeStatsLine in the title row and a second
            // ChargeSegmentBar under it. There is now one readout for both states (see the
            // Box above), so the copies are gone rather than merely hidden.
            //
            // The header still has to keep its hands off the space the readout occupies when
            // collapsed, which is what the reserve below does.
            titleTrailing = null,
""" + s[k:]
# headerContent -> a fixed reservation
i2 = s.index("            headerContent = {")
k2 = s.index("        ) {", i2)
s = s[:i2] + """            headerContent = {
                // A RESERVATION, not content. The morphing readout is absolutely positioned
                // against the card's Box, so the header has to leave room for it or the title
                // row and the numbers overlap while collapsed.
                //
                // A fixed height rather than a measured one: the collapsed readout is a
                // labelLarge line plus ChargeBarHeight plus their gap, all constants, so this
                // is derived from the same values the readout lays out from. Fades out as the
                // pebble opens because the expanded readout sits over the photo instead, and
                // the header must not keep holding 42dp it no longer needs.
                Spacer(Modifier.height(lerp(HeroReadoutReserve, 0.dp, heroT)))
            },
""" + s[k2:]
open(p, "w", encoding="utf-8", newline="").write(s)
print("titleTrailing -> null; headerContent -> reservation")
