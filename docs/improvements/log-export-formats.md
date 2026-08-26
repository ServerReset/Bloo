# Log Export Formats

## Overview
This document defines recommended export formats for the Bloo app's logging system, enabling users to share logs with support teams in structured, analyzable formats.

## Format Specifications

### 1. Plain Text Format (Current)
**Best for**: Direct sharing, email, instant messages
**Structure**: Newline-separated log entries

```
[07:30:15] Synced with Drive
[07:30:20] Loaded vehicle: 2024 Hyundai Ioniq 5
[07:30:22] Status updated for vehicle
[07:31:05] Climate set to 72°F
[07:35:10] Sync failed: Connection timeout
```

**Advantages:**
- Human-readable
- Universal compatibility
- Minimal file size
- Easy to search/grep

**Implementation:**
```kotlin
fun formatLogsAsPlainText(logs: List<String>, timestamps: List<Long> = emptyList()): String {
    return if (timestamps.isNotEmpty()) {
        logs.zip(timestamps).joinToString("\n") { (log, time) ->
            val formatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))
            "[$formatted] $log"
        }
    } else {
        logs.joinToString("\n")
    }
}
```

### 2. CSV Format (Recommended for Analysis)
**Best for**: Spreadsheet import, analytics, filtering
**Structure**: Timestamp, Category, Message

```csv
Timestamp,Category,Message
2026-08-26 07:30:15,SYNC,Synced with Drive
2026-08-26 07:30:20,GARAGE,Loaded vehicle: 2024 Hyundai Ioniq 5
2026-08-26 07:30:22,STATUS,Status updated for vehicle
2026-08-26 07:31:05,CLIMATE,Climate set to 72°F
2026-08-26 07:35:10,SYNC,Sync failed: Connection timeout
```

**Advantages:**
- Importable to Excel/Google Sheets
- Sortable and filterable
- Preserves structure
- Supports analysis

**Implementation:**
```kotlin
fun formatLogsAsCSV(logs: List<String>, 
                    timestamps: List<Long> = emptyList(),
                    categories: List<String> = emptyList()): String {
    val header = "Timestamp,Category,Message"
    val rows = logs.mapIndexed { index, log ->
        val timestamp = if (timestamps.isNotEmpty()) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamps[index]))
        } else "N/A"
        val category = if (categories.isNotEmpty()) categories[index] else "APP"
        // Escape quotes in message
        val escapedMessage = log.replace("\"", "\"\"")
        """$timestamp,"$category","$escapedMessage""""
    }
    return (listOf(header) + rows).joinToString("\n")
}
```

### 3. JSON Format (Machine-Readable)
**Best for**: API submission, programmatic processing
**Structure**: Structured metadata with arrays

```json
{
  "export": {
    "timestamp": "2026-08-26T07:30:15Z",
    "app_version": "1.2.3",
    "device_model": "Pixel 6",
    "android_version": 14,
    "session_duration_ms": 300000,
    "log_count": 5,
    "logs": [
      {
        "index": 0,
        "timestamp": "2026-08-26T07:30:15Z",
        "category": "SYNC",
        "message": "Synced with Drive",
        "level": "info"
      },
      {
        "index": 1,
        "timestamp": "2026-08-26T07:30:20Z",
        "category": "GARAGE",
        "message": "Loaded vehicle: 2024 Hyundai Ioniq 5",
        "level": "info"
      }
    ]
  }
}
```

**Advantages:**
- Parseable by programs
- Includes metadata
- Extensible
- Version-safe

**Implementation:**
```kotlin
data class LogEntry(
    val index: Int,
    val timestamp: String,
    val category: String,
    val message: String,
    val level: String = "info"
)

fun formatLogsAsJSON(logs: List<String>,
                     timestamps: List<Long> = emptyList(),
                     categories: List<String> = emptyList(),
                     appVersion: String = "unknown"): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    
    val entries = logs.mapIndexed { index, log ->
        LogEntry(
            index = index,
            timestamp = if (timestamps.isNotEmpty()) {
                dateFormat.format(Date(timestamps[index]))
            } else "unknown",
            category = if (categories.isNotEmpty()) categories[index] else "APP",
            message = log
        )
    }
    
    val export = mapOf(
        "export" to mapOf(
            "timestamp" to dateFormat.format(Date()),
            "app_version" to appVersion,
            "log_count" to logs.size,
            "logs" to entries
        )
    )
    
    return Json.encodeToString(export)
}
```

## Log Categories

Proposed categories for log classification:

| Category | Purpose | Color |
|----------|---------|-------|
| SYNC | Google Drive sync operations | Blue |
| GARAGE | Garage loading, vehicle status | Green |
| COMMANDS | Lock, unlock, climate, etc. | Orange |
| STATUS | Status updates, polling | Purple |
| AUTH | Login, logout, token refresh | Red |
| UI | Screen transitions, navigation | Gray |
| CLIMATE | Climate control operations | Orange |
| CHARGE | Charge management | Yellow |
| ERROR | Error messages and failures | Red |
| DEBUG | Debug-level information | Gray |

## Export Flow

### User Perspective
1. Open Settings > Advanced > Logs
2. Tap "Show" to expand logs
3. Tap format selector (dropdown or chips)
   - Plain Text
   - CSV
   - JSON
4. Tap "Export" or "Share"
5. Choose destination
   - Copy to clipboard
   - Share via Intent (Email, Messages, etc.)
   - Save to Downloads

### Implementation Steps
1. **Phase 1**: Plain Text with timestamps (current format)
2. **Phase 2**: Add CSV export button (spreadsheet analysis)
3. **Phase 3**: Add JSON export (programmatic submission)
4. **Phase 4**: Add category tagging and filtering UI

## Size Considerations

**Example sizes for 100 logs:**
- Plain text: ~2-3 KB
- CSV: ~3-4 KB
- JSON: ~5-7 KB

All formats remain well under clipboard limits (typical 1 MB+) and email attachment limits (typical 25 MB+).

## Error Handling

**When exporting logs:**
- Verify logs list is not empty before exporting
- Handle missing timestamps gracefully (show "N/A")
- Sanitize log messages (escape quotes, remove null bytes)
- Show user confirmation with file size
- Handle Intent failures (if sharing unavailable)

## Sample Export

### Scenario
User wants to report a sync failure to support.

**Best export format**: CSV
1. Open Settings > Logs
2. Select "CSV" format
3. Tap "Share"
4. Select "Gmail"
5. Email contains attachment with structured sync-related entries
6. Support team can:
   - Sort by timestamp
   - Filter by category
   - Find failure patterns
   - Correlate with server logs

## Future Enhancements

1. **Incremental export**: Export only logs since last export
2. **Filtering before export**: User selects date range or categories
3. **Compression**: ZIP export for large log volumes
4. **Server submission**: Direct upload to bug report system
5. **Obfuscation**: Remove sensitive vehicle data before export
6. **Correlation**: Include device logs, system errors
7. **Analytics**: Automatic error pattern detection
8. **Retention policies**: Keep logs for 30/90/365 days

## Related Code

### Current Log Collection
- `AppViewModel.logs: StateFlow<List<String>>`
- Methods: `reportInfo()`, `reportError()`, `reportWarning()`

### Timestamp Tracking
- Will require adding `timestamps: MutableList<Long>` to AppViewModel
- Update all log methods to add `System.currentTimeMillis()`

### Category Tracking
- Will require adding `categories: MutableList<String>` to AppViewModel
- Add category parameter to all log methods

## Testing Checklist

- [ ] Plain text export includes all logs
- [ ] CSV opens correctly in Excel
- [ ] JSON parses without errors
- [ ] Timestamps are in correct timezone
- [ ] Special characters handled properly
- [ ] Empty logs handled gracefully
- [ ] Large logs (1000+) export within reasonable time
- [ ] Share intent works (email, messaging)
- [ ] Clipboard paste works correctly

## Version History

- **v1.0** (Current): Plain text format design
- **v2.0** (Planned): CSV and JSON formats
- **v3.0** (Future): Filtering, categories, compression

## Notes

- All timestamps should be in UTC for consistency
- File sizes are acceptable for modern devices
- Formats are designed for both human and automated reading
- Privacy should be considered when exporting logs with sensitive data
