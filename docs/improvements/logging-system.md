# Bloo Logging System

## Overview
The Bloo app includes a comprehensive logging system for tracking app activity, errors, and state changes. Logs are accessible in Settings under the "Logs" section (Advanced mode required).

## Log Architecture

### Log Collection
- **Source**: `AppViewModel.logs` StateFlow
- **Storage**: In-memory List<String> (session-based, cleared on app restart)
- **Filtering**: Logs are added via `reportInfo()`, `reportError()`, and other methods
- **Retention**: All logs from current session; cleared via Settings "Clear" button

### Current Capabilities
1. **Copy** - Copy all logs to clipboard (newline-separated format)
2. **Clear** - Clear all accumulated logs from current session
3. **Display** - Monospace, scrollable text view with selection support

## Log Entry Structure

Logs are currently stored as simple strings without timestamp information. Typical log entries include:

```
Synced with Drive
Sync failed: Connection timeout
Loaded vehicle: 2024 Hyundai Ioniq 5
Status updated for vehicle
Climate set to 72°F
```

## Future Enhancements

### Planned Improvements

1. **Timestamp Support**
   - Add timestamp to each log entry
   - Format: `[HH:MM:SS] Log message`
   - Allows users to see when events occurred
   - Enables filtering by time range

2. **Log Categories**
   - Tag logs by category (sync, commands, UI, auth, etc.)
   - Allow filtering by category
   - Different color/icon indicators for each category

3. **Export Functionality**
   - **CSV Format**: Timestamp, Category, Message (easily importable)
   - **JSON Format**: Structured data with metadata
   - **Plain Text**: Current format with timestamps
   - Export via:
     - Share sheet (intent-based sharing)
     - Write to app cache for backup
     - Email integration

4. **Log Persistence**
   - Option to persist logs to app cache directory
   - Survive app restart
   - Automatic cleanup of old logs (keep last 7 days)
   - Settable retention policy

5. **Log Filtering UI**
   - Category filter chips
   - Time range picker
   - Search/text filter
   - Error-only toggle

6. **Log Analytics**
   - Frequency of errors by type
   - Performance metrics (sync times, command execution times)
   - Crash/exception logging
   - Analytics summary card in Settings

## Implementation Status

### Currently Done
- ✅ Basic log collection and display
- ✅ Copy to clipboard
- ✅ Clear logs
- ✅ Monospace formatting
- ✅ Scrollable view with selection

### Not Yet Implemented
- ⏳ Timestamps
- ⏳ Categories/filtering
- ⏳ Export to file formats
- ⏳ Log persistence
- ⏳ Analytics dashboard

## Usage Examples

### For Users
1. Open Settings (gear icon on any screen)
2. Toggle "Advanced" mode (top right)
3. Scroll to "Logs" section
4. Tap "Show" to expand logs
5. Use Copy to share logs with support
6. Use Clear to reset log history

### For Developers
Add logs via AppViewModel:
```kotlin
vm.reportInfo("Event completed successfully")
vm.reportError("Failed to sync: $errorMessage")
```

## Log Format Design Considerations

### Why Monospace?
- Easier to scan structured information
- Aligns related lines
- Standard for log viewing tools
- Better for copy-paste accuracy

### Why Session-Based?
- Simple implementation (no disk I/O)
- Privacy-friendly (logs don't persist)
- Fast retrieval
- Reduced app complexity

### Why Newline-Separated for Export?
- Compatible with standard text editors
- Easy to filter with command-line tools
- No special parsing required
- Preserves natural log format

## Best Practices

1. **For App Developers**
   - Keep messages concise but descriptive
   - Include relevant details (vehicle name, error code, timeout duration)
   - Use consistent terminology across the app
   - Avoid logging sensitive info (passwords, auth tokens)

2. **For Debugging**
   - Reproduce issue
   - Open Settings > Logs
   - Copy logs
   - Include in bug reports
   - Note the time when the issue occurred

3. **For Support**
   - Ask users to copy logs before clearing
   - Use timestamps to correlate with user-reported times
   - Look for patterns in error messages
   - Check for sync/network related entries

## Related Files
- `app/src/main/java/com/bloo/bluelink/ui/SettingsScreen.kt` (lines 1073-1150) - Logs UI
- `app/src/main/java/com/bloo/bluelink/ui/AppViewModel.kt` - Log collection and reporting methods
- `app/src/main/java/com/bloo/bluelink/data/SettingsStore.kt` - Settings persistence

## Notes
- Logs are cleared when the app is fully closed/killed
- Logs can accumulate large amounts of text in long sessions
- Copy functionality uses system clipboard (limited size on some devices)
- Monospace rendering requires device font support

## Version History
- **v1.0** (Current): Basic log collection, copy, clear functionality
