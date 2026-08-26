# Compose Animation Patterns in Bloo

## Overview
This guide documents the animation patterns used throughout the Bloo app using Jetpack Compose. Understanding these patterns ensures consistent, performant animations across the application.

## Core Animation Philosophy

The Bloo app follows Material Design 3 principles with an emphasis on:
- **Spring-based physics** for natural, responsive motion
- **Layered composition** to prevent recomposition storms
- **Performance-aware animation** with graphicsLayer and draw-phase optimization
- **Unified spring specs** across related animations for visual consistency

## Spring Specifications

### Standard Spring Curves

| Name | Damping Ratio | Stiffness | Use Case |
|------|---------------|-----------|----------|
| **SoftDamping** | 0.8f | StiffnessMediumLow | Gentle, settling arrivals |
| **DampingRatioMediumBouncy** | 0.7f | StiffnessMedium | Subtle bounce, playful feel |
| **DampingRatioNoBouncy** | 1.0f | StiffnessMediumLow | Clean, no-overshoot arrivals |
| **DampingRatioLowBouncy** | 0.5f | StiffnessMediumLow | Pronounced bounce, energetic |

### Motion Behavior by Spec

**MediumBouncy (Docking arrivals)**
```kotlin
spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)
```
- Used for: Floating pill docking, TitleFlightOverlay arrival
- Effect: Slight overshoot, settles gracefully
- Duration: ~400-500ms

**NoBouncy (Clean exits)**
```kotlin
spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)
```
- Used for: Undocking animations, scroll-driven animations
- Effect: Direct path, no bounce
- Duration: ~300-400ms

**SoftDamping (General-purpose)**
```kotlin
spring(
    dampingRatio = SoftDamping,  // 0.8f
    stiffness = Spring.StiffnessMediumLow
)
```
- Used for: Scale animations, opacity fades
- Effect: Smooth, natural settlement
- Duration: ~200-300ms

## Animation Categories

### 1. Floating Elements (Pills, Badges, Overlays)

**Pattern**: Docking/Undocking with MediumBouncy arrival

```kotlin
// In TitleFlightOverlay
val progress = remember { Animatable(0f) }
LaunchedEffect(docked) {
    if (!mounted) {
        mounted = true
        progress.snapTo(if (docked) 1f else 0f)
        return@LaunchedEffect
    }
    progress.animateTo(
        if (docked) 1f else 0f,
        spring(
            dampingRatio = if (docked) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )
}
```

**Key Points**:
- First composition snaps to target (no visible animation)
- Subsequent state changes animate with spring
- Asymmetric springs: Bouncy arriving, clean leaving
- Used for: Flying names, floating controls, badge positioning

### 2. Scroll-Driven Animations

**Pattern**: Continuous interpolation based on scroll fraction

```kotlin
// In GarageScreen overlays
val overlayShiftTarget = if (state.value.refreshing) RefreshPullShift
    else (RefreshPullShift * pullFraction).coerceIn(0.dp, RefreshPullShift)
val refreshShiftState = animateDpAsState(
    targetValue = overlayShiftTarget,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (state.value.refreshing) Spring.StiffnessLow else Spring.StiffnessMedium
    )
)
```

**Key Points**:
- Respond immediately to scroll (fraction-based, not threshold-based)
- Use NoBouncy spec for predictable, on-screen tracking
- Shift stiffness based on state (faster during pull, slower during settle)
- Used for: Pull-to-refresh indicator shifting, overlay tracking

### 3. Pebble Expand/Collapse

**Pattern**: Coordinated content reveal with staggered heights

```kotlin
// Standard pebble animation specs
val spec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
(fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith
    (fadeOut(spec) + scaleOut(spec, targetScale = 0.94f))
```

**Key Points**:
- Combined fade + scale for dimensionality
- 94% initial scale prevents jarring pop
- Used consistently across pebble opens
- Used for: Card expand/collapse, animated content reveals

### 4. Pull-to-Refresh Loading Indicator

**Pattern**: Spring-based position animation with progress-driven alpha

```kotlin
// In Pebbles.kt Refreshable
PullToRefreshDefaults.LoadingIndicator(
    state = ptrState,
    isRefreshing = state.refreshing,
    modifier = Modifier
        .align(Alignment.TopCenter)
        .offset {
            val indicatorProgress = if (state.refreshing) 1f else ptrState.distanceFraction.coerceIn(0f, 1f)
            val offScreenPx = -(topInset + 56.dp).roundToPx()
            val onScreenPx = (topInset + 28.dp).roundToPx()
            IntOffset(0, offScreenPx + ((onScreenPx - offScreenPx) * indicatorProgress).roundToInt())
        }
)
```

**Key Points**:
- Indicator grows from off-screen to on-screen position
- Position driven by pull progress (0 → 1)
- Alpha controlled by same progress value
- Used for: Pull-to-refresh, swipe-to-refresh across screens

## Performance Optimization Patterns

### 1. Composition Scope Splitting

**Problem**: Animation frame recomposes all content

**Solution**: Move animation to small child scope

```kotlin
// ❌ BAD: Entire button recomposes every frame
@Composable
fun MorphButtonBad() {
    val scale by animateFloatAsState(targetValue = if (active) 1f else 0.8f)
    Box(Modifier.scale(scale)) {
        // Expensive content...
    }
}

// ✅ GOOD: Only chrome recomposes
@Composable
fun MorphButtonGood() {
    Box {
        MorphChrome()  // Recomposes with animation
        Content()  // Stable, never recomposes
    }
}
```

**Used in**: MorphButtonCore, TitleFlightOverlay, Pebbles expand animations

### 2. Draw-Phase Only Reading

**Problem**: Composition-scope reads of animation state trigger recomposition

**Solution**: Read state only in lambda (layout/draw phase)

```kotlin
// ❌ BAD: Reads fraction in composition
val indicatorProgress = ptrState.distanceFraction  // Recomposition triggered!
Box(Modifier.offset { ... })

// ✅ GOOD: Read only in offset lambda (layout phase)
Box(Modifier.offset {
    val indicatorProgress = ptrState.distanceFraction  // Layout phase only
    IntOffset(...)
})
```

**Used in**: Pull-to-refresh indicators, scroll-driven overlays

### 3. graphicsLayer for Press Animation

**Pattern**: Scale without layout inflation

```kotlin
Box(
    Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .background(color)
)
```

**Key Points**:
- graphicsLayer applies transform at draw time
- Doesn't trigger parent layout recalculation
- Used for press effects, subtle transforms
- Used in: MorphButtonCore press scale, float fun mode animations

### 4. Staggered Animations with placeWithLayer

**Pattern**: Per-item compositing layer for staggered reveal

```kotlin
// In StaggeredRevealColumn
ReorderColumn(...) { item ->
    Box(Modifier.animatePlacement()) {
        Surface(Modifier.placeWithLayer { ... }) {
            Content()
        }
    }
}
```

**Key Points**:
- Each row gets its own compositing layer
- Prevents sibling recomposition cascade
- Safe for nested animations
- Used in: Settings card expansion, pebble list reveals

## Animation Timing Guidelines

| Animation | Duration | Easing | Trigger |
|-----------|----------|--------|---------|
| **Floating pill arrival** | 400-500ms | MediumBouncy | Docking threshold crossed |
| **Floating pill departure** | 300-400ms | NoBouncy | Undocking triggered |
| **Scroll tracking** | Instant | NoBouncy | Continuous (scroll drag) |
| **Pebble expand** | 200-300ms | SoftDamping | User tap/chevron click |
| **Pull-to-refresh** | Live + 200ms settle | Variable | Pull gesture + release |
| **Page transition** | 300-500ms | Spring | Navigation |
| **Floating toggle** | 200-250ms | SoftDamping | User input |

## Common Mistakes

### 1. ❌ Recomposing Parent on Animation

```kotlin
// Bad: Parent recomposes every frame
Box {
    val scale by animateFloatAsState(...)  // <-- Parent scope
    Text("Label")  // Recomposes every frame!
}
```

### 2. ❌ Reading Animation State in Composition

```kotlin
// Bad: Reads pull state in composition
val offset = ptrState.distanceFraction * 50.dp  // Triggers recomposition!
Box(Modifier.offset(offset))
```

### 3. ❌ Overlapping Spring Specs

```kotlin
// Bad: Inconsistent feel
Button(..., animationSpec = spring(0.5f, 100f))  // Custom, non-standard
```

### 4. ❌ Forgetting graphicsLayer on Press

```kotlin
// Bad: Scales with layout, causes jank
Box(Modifier.scale(pressScale))  // Triggers layout recalc!
```

## Best Practices

✅ **DO:**
- Use standard spring specs (SoftDamping, MediumBouncy, NoBouncy)
- Move animations to small child scopes
- Read animation state only in offset/graphicsLayer lambdas
- Use graphicsLayer for transforms (scale, rotate, alpha)
- Document why each animation exists
- Test with large lists/grids for performance

❌ **DON'T:**
- Create custom spring specs for one-off animations
- Read animation state in composition scope
- Animate layout properties (width, height, padding)
- Nest animations without placeWithLayer boundaries
- Use tween() for interactive animations
- Forget that first mount should snap, not animate

## Testing Animations

### Compose Preview Annotations

```kotlin
@Preview(showBackground = true)
@Composable
fun FloatingPillPreview() {
    // Shows animation in real-time
    FloatingPill(docked = true)
}
```

### Debug Animation Speed

```kotlin
// In Build.gradle.kts
debugImplementation("androidx.compose.animation:animation-debug")

// In Logcat filter
adb shell settings put global animator_duration_scale 10.0  // 10x slower
```

### Profiling

- Use Compose Performance profiler
- Watch for recomposition spikes during animation
- Profile layout/draw phases separately
- Check graphicsLayer usage

## References

- **Material Design 3 Motion**: https://m3.material.io/styles/motion
- **Compose Animation Docs**: https://developer.android.com/jetpack/compose/animation
- **Spring Physics**: https://developer.android.com/jetpack/compose/animation/spring
- **Performance**: https://developer.android.com/jetpack/compose/performance

## Related Files

- `uicommon/src/main/java/com/bloo/uicommon/MorphButtonCore.kt` - Button animations
- `app/src/main/java/com/bloo/bluelink/ui/TitleFlight.kt` - Floating pill animations
- `app/src/main/java/com/bloo/bluelink/ui/Pebbles.kt` - Pebble animations
- `app/src/main/java/com/bloo/bluelink/ui/UiTokens.kt` - Animation specs (collapseEnter/Exit)

## Version History

- **v1.0** (Current): Core animation patterns documented
- **v2.0** (Planned): Detailed performance profiling guide
- **v3.0** (Planned): Animation debugging tools tutorial
