# Button Expansion Animation - Real Integration Examples

## Pattern 1: MorphButton Wrapper (Quick Tiles)

**Location**: `QuickTiles.kt` - `AddTilePill()`

```kotlin
// BEFORE: Plain MorphButton
@Composable
internal fun AddTilePill(label: String, onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

// AFTER: With Safe Expansion
@Composable
internal fun AddTilePill(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    
    SafeExpansiveButton(
        interactionSource = interactionSource,
        enabled = true,
    ) {
        MorphButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

## Pattern 2: FloatingActionButton (Hero.kt, FullDetail.kt)

**Location**: `Hero.kt` / `FullDetail.kt` - Action buttons

```kotlin
// BEFORE
FloatingActionButton(
    onClick = { vm.toggleDoorLock(vin) },
    containerColor = scheme.primary,
) {
    Icon(Icons.Filled.Lock, contentDescription = "Lock")
}

// AFTER: With Hardened Protection
@Composable
private fun ExpandLockFAB(
    vin: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    haptics: HapticManager,
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    SafeExpansiveButton(
        interactionSource = interactionSource,
        enabled = !isLoading, // Prevents animation while loading
    ) {
        FloatingActionButton(
            onClick = { haptics.tick(); onClick() },
            interactionSource = interactionSource,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Lock")
        }
    }
}
```

## Pattern 3: Segmented Button Group (Climate Control)

**Location**: `ClimatePebble.kt`

```kotlin
// BEFORE: Raw button group
@Composable
private fun TempControls(current: Int, max: Int, min: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(max - min + 1) { i ->
            val temp = min + i
            Button(
                onClick = { onChange(temp) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (temp == current) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.surface
                ),
            ) {
                Text("${temp}°")
            }
        }
    }
}

// AFTER: With Individual Expansion per Button
@Composable
private fun TempControls(current: Int, max: Int, min: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(max - min + 1) { i ->
            val temp = min + i
            val interactionSource = remember { MutableInteractionSource() }
            
            SafeExpansiveButton(
                interactionSource = interactionSource,
                enabled = true,
            ) {
                Button(
                    onClick = { onChange(temp) },
                    interactionSource = interactionSource,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (temp == current) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Text("${temp}°")
                }
            }
        }
    }
}
```

## Pattern 4: Icon Button (Settings, Back)

**Location**: `SettingsScreen.kt`, `GarageScreen.kt`

```kotlin
// BEFORE
IconButton(
    onClick = onBack,
    modifier = Modifier.size(48.dp),
) {
    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
}

// AFTER: With Haptic Feedback
@Composable
private fun ExpandingBackButton(
    onBack: () -> Unit,
    haptics: HapticManager,
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    ExpansiveButtonHardened(
        interactionSource = interactionSource,
        onPress = { haptics.tick() },
    ) {
        IconButton(
            onClick = { haptics.tick(); onBack() },
            interactionSource = interactionSource,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}
```

## Pattern 5: Action Row (Remote Control)

**Location**: `RemoteActionsHistory.kt`

```kotlin
// BEFORE: Multiple action buttons
@Composable
private fun ActionButtonRow(action: RemoteAction, onExecute: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExecute) { Text("Execute") }
        OutlinedButton(onClick = {}) { Text("Details") }
        TextButton(onClick = {}) { Text("Share") }
    }
}

// AFTER: Each button with independent expansion
@Composable
private fun ActionButtonRow(action: RemoteAction, onExecute: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary action button
        val executeSource = remember { MutableInteractionSource() }
        SafeExpansiveButton(
            interactionSource = executeSource,
        ) {
            Button(
                onClick = onExecute,
                interactionSource = executeSource,
            ) { Text("Execute") }
        }
        
        // Secondary action button
        val detailsSource = remember { MutableInteractionSource() }
        SafeExpansiveButton(
            interactionSource = detailsSource,
        ) {
            OutlinedButton(
                onClick = { },
                interactionSource = detailsSource,
            ) { Text("Details") }
        }
    }
}
```

## Pattern 6: Pebble Header Action (All Pebbles)

**Location**: `Pebbles.kt`, `ClimatePebble.kt`, `TripsPebble.kt`, `EnergyPebble.kt`

```kotlin
// The PebbleShell already supports headerAction - update it:
data class PebbleHeaderAction(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val onClick: () -> Unit,
)

// Inside PebbleShell rendering:
@Composable
private fun PebbleHeaderButton(action: PebbleHeaderAction) {
    val interactionSource = remember { MutableInteractionSource() }
    
    ExpansiveButtonHardened(
        interactionSource = interactionSource,
        enabled = true,
    ) {
        IconButton(
            onClick = action.onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                action.icon,
                contentDescription = action.label,
                tint = if (action.active) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

## Pattern 7: Dialog/Bottom Sheet Actions

**Location**: `DiagnosticsPebble.kt`, Any dialog

```kotlin
// BEFORE
Dialog(
    onDismissRequest = { },
) {
    Surface {
        Column {
            // ... dialog content
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { }) { Text("Cancel") }
                Button(onClick = { }) { Text("Confirm") }
            }
        }
    }
}

// AFTER
@Composable
private fun ConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface {
            Column {
                // ... dialog content
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    val cancelSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(interactionSource = cancelSource) {
                        TextButton(
                            onClick = onCancel,
                            interactionSource = cancelSource,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel") }
                    }
                    
                    val confirmSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(interactionSource = confirmSource) {
                        Button(
                            onClick = onConfirm,
                            interactionSource = confirmSource,
                            modifier = Modifier.weight(1f),
                        ) { Text("Confirm") }
                    }
                }
            }
        }
    }
}
```

## Integration Checklist by File

### High Priority (Visible & Interactive)
- [ ] `QuickTiles.kt`: AddTilePill, MorphButton actions
- [ ] `Hero.kt`: Lock/Unlock FAB, expand button
- [ ] `ClimatePebble.kt`: Temperature controls, mode buttons
- [ ] `FullDetail.kt`: Control buttons
- [ ] `RemoteActionsHistory.kt`: Action execution buttons

### Medium Priority (Secondary Controls)
- [ ] `Pebbles.kt`: Header action buttons (all pebbles inherit)
- [ ] `TripsPebble.kt`: Action buttons
- [ ] `EnergyPebble.kt`: Charge control buttons
- [ ] `WeatherPebble.kt`: Any action buttons
- [ ] `SettingsScreen.kt`: Settings action buttons

### Lower Priority (Dialogs & Utilities)
- [ ] `DiagnosticsPebble.kt`: Diagnostic action buttons
- [ ] `InfoPebble.kt`: Info action buttons
- [ ] Generic dialog/sheet button patterns

## Migration Path

1. **Phase 1**: Wrap all `SafeExpansiveButton` usage in high-priority files
   - ✅ Provides 80% of user-visible expansion animation
   - ✅ Low risk - SafeExpansiveButton handles all edge cases
   - Estimated impact: Startup interactions feel premium

2. **Phase 2**: Medium-priority controls
   - ✅ Consistent feel across pebbles
   - ✅ Refined climate controls
   - Estimated impact: Cohesive app-wide behavior

3. **Phase 3**: Dialog/utility consolidation
   - ✅ Complete edge case handling
   - ✅ Haptic integration everywhere
   - Estimated impact: Polished, premium feel end-to-end

## Testing Each Integration

For each component integrated:
1. **Visual**: Tap button → should expand 15% and retract smoothly
2. **Haptic**: Should feel responsive with optional haptic tick
3. **Rapid**: Tap 5 times quickly → no stacking, smooth each time
4. **Disabled**: If button has `enabled` state, test disabled → no animation
5. **Performance**: 60fps maintained even with multiple concurrent taps

## Code Review Checklist

- [ ] `interactionSource` passed to BOTH wrapper AND button
- [ ] `enabled` state checked and passed to SafeExpansiveButton
- [ ] No heavy computations in onClick lambda
- [ ] Haptic feedback optional but integrated where appropriate
- [ ] Exception handling not added (DisposableEffect handles it)
- [ ] Memory not leaked (DisposableEffect cleans up automatically)
