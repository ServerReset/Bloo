# Unified Floating Names System - Architecture Proposal

## Overview

Currently, the Bloo app displays floating car names in two separate implementations:
1. **Hero car name pill** (garage/car detail screens) - Uses TitleFlightOverlay
2. **Settings screen name pill** - Uses separate TitleFlightOverlay instance

Both use identical animation patterns but are independently maintained. This proposal consolidates them into a **single, reusable FloatingNamePill composable** that knows its context and adapts accordingly.

## Current State

### Existing Implementations

**Hero Car Name** (`TitleFlight.kt`, lines 732+)
```
TitleFlightOverlay(
    flight = hoistedFlight.flight,
    cornerX = 16.dp,
    cornerY = hoistedTopInset + HeaderCornerGap,
    reserveEnd = 72.dp,
    maxWidth = screenWidth - 16.dp - 72.dp - 32.dp,
    textColorOverride = null,  // Reads from flight's live color
    onClick = { pillScope.launch { hoistedScrollToTop.value?.invoke() } },
    onNameBoundsChanged = { nameBoundsPxState.value = it },
    onSettledChanged = { dockedPages.value = it },
    extraContent = null,
    ...
)
```

**Settings Screen Name** (`SettingsScreen.kt`, lines 2023+)
```
TitleFlightOverlay(
    flight = local.flight,
    cornerX = if (embedded) 16.dp else 60.dp,
    cornerY = topInset + HeaderCornerGap,
    reserveEnd = 192.dp,
    maxWidth = screenWidth - cornerX - 192.dp - 32.dp,
    textColorOverride = MaterialTheme.colorScheme.onSurface,  // Fixed
    onClick = { settingsScope.launch { settingsGridState.animateScrollToItem(0) } },
    onSettledChanged = { onDockedChanged?.invoke(it) },
    ...
)
```

**Shared Differences:**
- Corner positioning (X, Y offsets)
- Reserved space on right (for Settings toggle vs. gear button)
- Text color (dynamic photo-based vs. static onSurface)
- Click action (scroll to top vs. smooth scroll)
- Extra content (Settings page label vs. none)
- Bounds callback usage (page-dot collision vs. hoisted badge handoff)

## Proposed Solution

### New Architecture

```kotlin
/**
 * Unified floating name pill composable that adapts to its context.
 * Works for both hero car names and Settings screen names.
 */
@Composable
fun FloatingNamePill(
    context: FloatingNameContext,  // Specifies which screen
    name: String,                   // The name to display ("Settings", "2024 Ioniq 5", etc.)
    isDockedForContext: Boolean,    // Whether it's docked for this specific context
    onNameBoundsChanged: ((Rect?) -> Unit)? = null,  // For collision detection
    onDockedChanged: ((Boolean) -> Unit)? = null,     // For state management
    onScrollToTop: (() -> Unit)? = null,              // For click action
    extraContent: (@Composable RowScope.() -> Unit)? = null,  // Page label, etc.
)
```

### Context Enum

```kotlin
enum class FloatingNameContext {
    HERO_CAR,      // Garage / car detail screens
    SETTINGS,      // Settings screen (standalone)
    SETTINGS_EMBEDDED,  // Settings page in pager
}

// Extension with resolved values
val FloatingNameContext.config: FloatingNameConfig get() = when (this) {
    HERO_CAR -> FloatingNameConfig(
        cornerX = 16.dp,
        reserveEnd = 72.dp,
        usePhotoColor = true,
        label = null
    )
    SETTINGS -> FloatingNameConfig(
        cornerX = 60.dp,
        reserveEnd = 192.dp,
        usePhotoColor = false,
        label = null
    )
    SETTINGS_EMBEDDED -> FloatingNameConfig(
        cornerX = 16.dp,
        reserveEnd = 192.dp,
        usePhotoColor = false,
        label = null  // Page label passed as extraContent
    )
}
```

## Migration Plan

### Phase 1: Create New Composable (Non-Breaking)

1. Create `FloatingNamePill.kt` in `ui/` with unified implementation
2. Internally use `TitleFlightOverlay` (unchanged backend)
3. Adapt parameters based on context enum
4. No changes to existing call sites yet

**Timeline**: 1-2 hours
**Risk**: Low (new code, no existing callers)

### Phase 2: Refactor Call Sites (Gradual)

1. Update GarageScreen to use `FloatingNamePill(FloatingNameContext.HERO_CAR, ...)`
2. Update SettingsScreen to use `FloatingNamePill(FloatingNameContext.SETTINGS, ...)`
3. Verify animations match originals
4. Delete old TitleFlightOverlay calls (keep TitleFlightOverlay class for other uses)

**Timeline**: 2-3 hours
**Risk**: Medium (animation behavior must match exactly)

### Phase 3: Potential Future Refactoring

Once stable, consider:
- Consolidating TitleFlightOverlay's internal complexity
- Creating unified context-aware state holder (HeroTitleFlight wrapper)
- Adding other floating elements (avatar pills, status badges)

**Timeline**: Later iteration
**Risk**: Lower (only after Phase 1-2 proven stable)

## Implementation Details

### Parameter Mapping

| Parameter | HERO_CAR | SETTINGS | SETTINGS_EMBEDDED |
|-----------|----------|----------|-------------------|
| cornerX | 16.dp | 60.dp | 16.dp |
| cornerY | `topInset + HeaderCornerGap` | `topInset + HeaderCornerGap` | Same |
| reserveEnd | 72.dp | 192.dp | 192.dp |
| maxWidth | `screenWidth - 88.dp - 32.dp` | `screenWidth - cornerX - 224.dp` | Same |
| textColorOverride | null (photo-based) | onSurface | onSurface |
| onClick | scroll to top | scroll grid to top | scroll grid to top |
| extraContent | null | page label | page label |
| onNameBoundsChanged | for pager-dots | for hoisted badge | for hoisted badge |

### Color Resolution Logic

```kotlin
private fun resolveTextColor(
    context: FloatingNameContext,
    flight: TitleFlightSource?,
    scheme: ColorScheme
): Color = when (context) {
    HERO_CAR -> flight?.readColor() ?: scheme.onSurface  // Dynamic
    SETTINGS, SETTINGS_EMBEDDED -> scheme.onSurface  // Static
}
```

### Scroll Action Logic

```kotlin
private fun resolveOnClick(
    context: FloatingNameContext,
    heroScrollAction: (() -> Unit)?,
    settingsScrollAction: (() -> Unit)?
): (() -> Unit)? = when (context) {
    HERO_CAR -> heroScrollAction
    SETTINGS, SETTINGS_EMBEDDED -> settingsScrollAction
}
```

## Benefits

✅ **Code Reuse**: Single source of truth for floating name logic
✅ **Consistency**: Both locations use identical animation specs
✅ **Maintainability**: Changes to animation logic apply everywhere
✅ **Extensibility**: Easy to add new contexts (watch app, new screens)
✅ **Type Safety**: Enum ensures correct parameter combinations
✅ **Documentation**: Self-documenting through context

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Animation behavior changes | Intensive visual testing; keep TitleFlightOverlay unchanged; compare frame-by-frame |
| Integration bugs | Gradual migration; test each context separately before merging |
| Naming collision | Unique context enum prevents mistakes; IDE refactoring helps |
| Hidden edge cases | Document all discovered quirks in FloatingNameContext extension |

## Testing Strategy

### Visual Testing
- Screenshot comparison (hero car name pill)
- Screenshot comparison (Settings name pill)
- Verify animations match original frame-by-frame
- Test on multiple screen sizes/densities

### Functional Testing
- Verify docking/undocking triggers correctly
- Test onClick actions (scroll to top works)
- Verify collision detection (pager dots)
- Test state callbacks fire at right times

### Integration Testing
- Verify no regressions in car detail screens
- Verify Settings screen transitions work
- Test full-app navigation flow

## Code Example

### Before (Hero Car)

```kotlin
TitleFlightOverlay(
    flight = hoistedFlight.flight,
    cornerX = 16.dp,
    cornerY = hoistedTopInset + HeaderCornerGap,
    // ... 10 more parameters
)
```

### After (Hero Car)

```kotlin
FloatingNamePill(
    context = FloatingNameContext.HERO_CAR,
    name = currentVehicle?.name ?: "Car",
    isDockedForContext = hoistedDocked,
    onNameBoundsChanged = { nameBoundsPxState.value = it },
    onScrollToTop = { hoistedScrollToTop.value?.invoke() }
)
```

**Much clearer intent; parameters auto-resolved by context.**

## Success Criteria

- ✅ New FloatingNamePill works identically to existing implementations
- ✅ No visual differences in animations
- ✅ Both call sites successfully migrated
- ✅ All CI tests pass
- ✅ Performance metrics unchanged
- ✅ Code coverage maintained or improved

## Estimated Effort

| Phase | Estimate | Confidence |
|-------|----------|------------|
| Phase 1 (New composable) | 1-2 hours | High |
| Phase 2 (Migration) | 2-3 hours | Medium |
| Phase 3 (Optimization) | TBD | TBD |
| **Total** | **3-5 hours** | **Medium-High** |

## References

- `TitleFlight.kt` - TitleFlightOverlay implementation
- `GarageScreen.kt` - Hero car name usage
- `SettingsScreen.kt` - Settings name usage
- `UiTokens.kt` - Animation specs (HeaderCornerGap, etc.)

## Next Steps

1. **Review**: Get feedback on this architecture
2. **Prototype**: Create FloatingNamePill.kt with both contexts
3. **Test**: Verify animations match originals
4. **Migrate**: Update call sites
5. **Verify**: Run full visual test suite
6. **Deploy**: Merge to main

## Questions & Discussions

- Should we consolidate TitleFlightOverlay itself, or keep it internal?
- Are there other floating elements that should use this pattern?
- Should the name be configurable per-context, or locked by enum?
- What's the appetite for abstracting even more (e.g., FloatingBadge)?

## Version History

- **v1.0** (Current): Proposal for unified floating names system
- **v2.0** (Next): Implementation plan with code examples
- **v3.0** (Future): Post-implementation retrospective
