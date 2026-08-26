# Pull-to-Refresh Implementation

## Overview
Pull-to-refresh functionality has been implemented across the app using Material 3's native `PullToRefresh` API. This document describes the implementation details and integration points.

## Implementation Details

### Core Components
- `rememberPullToRefreshState()` - State management for pull gesture tracking
- `.pullToRefresh()` modifier - Applied to scrollable containers
- `PullToRefreshDefaults.LoadingIndicator()` - Visual indicator for loading state

### Spring Animation
The loading indicator uses spring-based animation for smooth transitions:
- **Position**: Animates from off-screen (top) to on-screen as user pulls
- **Alpha**: Fades in as pull progresses (0f → 1f)
- **Scale**: Indicator appears at proper size (no scaling, unlike the flying pill)

## Screens Implemented

### 1. SettingsScreen (`SettingsScreen.kt`, line ~350)
**Purpose**: Trigger Google Drive sync from Settings screen

**Implementation**:
- Applied to `LazyVerticalStaggeredGrid` in the main settings grid
- Calls `vm.syncNow()` on refresh (syncs all data with Google Drive)
- Uses `state.syncing` to track loading state
- Indicator positioned at `topInset + 28.dp` when on-screen

**Trigger**: Swipe down from top of Settings screen

**Status Indicator**: Loading spinner appears at top center, animates position/alpha based on pull distance

### 2. EmptyScreen (`Guard.kt`, line ~710)
**Purpose**: Support refresh actions on signed-out or load-failed states

**Implementation**:
- Applied to main `Box(Modifier.fillMaxSize())`
- Conditional refresh action:
  - No accounts: Opens Settings (user needs to sign in)
  - Load failed: Calls `vm.loadGarage()` to retry
- Uses `state.loading` to track progress
- Indicator positioned at `topInset + 28.dp` when on-screen

**Trigger**: Swipe down from top of EmptyScreen

**Status**: Appropriate action taken based on the failure reason

## Indicator Positioning Logic

The loading indicator uses a spring-based animation with smooth offset transitions:

```kotlin
PullToRefreshDefaults.LoadingIndicator(
    state = ptrState,
    isRefreshing = state.syncing,  // or state.loading for EmptyScreen
    modifier = Modifier
        .align(Alignment.TopCenter)
        .offset {
            val indicatorProgress = if (state.syncing) 1f else ptrState.distanceFraction.coerceIn(0f, 1f)
            val offScreenPx = -(topInset + 56.dp).roundToPx()     // Hidden above status bar
            val onScreenPx = (topInset + 28.dp).roundToPx()       // Visible position
            IntOffset(0, offScreenPx + ((onScreenPx - offScreenPx) * indicatorProgress).roundToInt())
        },
)
```

### Offset Calculation Explanation
- **offScreenPx**: Negative value pushes indicator above status bar (hidden)
- **onScreenPx**: Positive value positions below status bar (visible)
- **Interpolation**: Linearly interpolates between the two based on progress (0 → 1)
- **During refresh**: Stays at onScreenPx position (indicatorProgress = 1f)
- **After completion**: Animates back to offScreenPx (spring animation)

## Integration Pattern

### Pattern (Consistent Across All Screens)

1. **State Creation** (in composable function):
   ```kotlin
   val ptrState = rememberPullToRefreshState()
   ```

2. **Modifier Application** (on scrollable container):
   ```kotlin
   .pullToRefresh(
       isRefreshing = state.syncingProperty,
       state = ptrState,
       onRefresh = { haptics?.diceRoll(); vm.refreshAction() },
   )
   ```

3. **Indicator Placement** (inside Box parent of scrollable):
   ```kotlin
   PullToRefreshDefaults.LoadingIndicator(
       state = ptrState,
       isRefreshing = state.syncingProperty,
       modifier = Modifier
           .align(Alignment.TopCenter)
           .offset { /* positioning logic */ },
   )
   ```

## Status Indicators

### SettingsScreen
- **Syncing**: Loading spinner visible, positioned at top
- **Complete**: Spinner fades out, settles below status bar
- **Duration**: Determined by actual sync time

### EmptyScreen
- **Loading**: Spinner visible during refresh action
- **For "no accounts"**: Settings navigation happens immediately
- **For "load failed"**: Garage load operation drives progress

## Haptic Feedback

Both implementations include haptic feedback:
- **On pull**: `haptics?.diceRoll()` - Tactile feedback when refresh action triggers
- **On completion**: Haptic settle feedback (handled by vm operations)

## Performance Considerations

### Layout Phase Only
- The `offset { }` lambda runs during layout phase, not composition
- Indicator repositioning doesn't trigger full recomposition of scrollable content
- Minimal performance impact on the main content grid/list

### State Management
- `ptrState.distanceFraction` is a State<Float>, read only in offset lambda
- Composition only recomposes when actual refresh state changes
- The indicator itself animates smoothly without extra recompositions

## Future Enhancements

### Potential Improvements
1. Add pull-to-refresh to other scrollable screens (e.g., GarageScreen pager)
2. Customize indicator appearance (colors, animation speed)
3. Add haptic patterns specific to different refresh actions
4. Support multiple simultaneous refresh actions with staggered completion

### Not Yet Implemented
- Login screen (refresh not appropriate during authentication)
- Onboarding screens (linear flow, not suitable for refresh)
- Car detail expanded view (refresh handled by per-car Refreshable wrapper in Pebbles)

## Testing Checklist

- [ ] Pull from top triggers refresh action
- [ ] Loading indicator appears and animates
- [ ] Haptic feedback plays on pull
- [ ] Settings: Sync completes and indicator retracts
- [ ] EmptyScreen (no accounts): Settings screen opens
- [ ] EmptyScreen (load failed): Garage reload completes
- [ ] Pull-to-refresh works with different screen sizes
- [ ] Indicator positioning is consistent across rotation/folds

## Related Files

- `SettingsScreen.kt` - Settings pull-to-refresh implementation
- `Guard.kt` - EmptyScreen pull-to-refresh implementation
- `Pebbles.kt` - Existing Refreshable wrapper (reference implementation)
- `TitleFlight.kt` - Floating pill animations (not directly related but uses similar spring patterns)

## Notes

- Pull-to-refresh follows Material Design 3 specifications
- Spring animations (DampingRatioMediumBouncy, StiffnessMediumLow) provide familiar, responsive feel
- Indicator positioning accounts for status bar insets for proper spacing
- All implementations handle loading state and completion animations correctly
