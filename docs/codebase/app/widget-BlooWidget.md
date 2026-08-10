# app: widget (superseded doc — read widget-CarWidget.md)

This file documented an earlier single-file `BlooWidget`/`BlooWidgetReceiver`
implementation (1×1–5×5, six size buckets) that no longer exists. The widget
was rebuilt around a continuous, unit-tested sizing model — `WidgetGrid` (the
launcher grid, 2–7 columns × 1–7 rows), `WidgetTier`/`tierFor` (18 tiers,
picked from the exact measured size, not a bucket), `Scale`/`WidgetLayout`
(the pure sizing-and-budget layer, split out specifically so it can be swept
by a JVM test instead of only checked by eye), and `WidgetConfig`/
`WidgetConfigActivity` (per-widget settings, including AUTO/ALWAYS/OFF
button-label display).

**Read [`widget-CarWidget.md`](widget-CarWidget.md) instead.** It documents
the current `CarWidget`/`CarWidgetReceiver` classes and the six files the
implementation is now split across.

Other docs in this tree that still say `BlooWidget`/`BlooWidgetReceiver` in
prose (`widget-workers-and-config.md`, the `AppViewModel-part*.md` files,
`wear-bridge-phone-side.md`, `snapshot-and-cache.md`,
`build-and-manifests.md`) mean `CarWidget`/`CarWidgetReceiver` — those
references haven't been swept yet.
