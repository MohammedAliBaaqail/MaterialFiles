# AIDL Fix for Windows Unicode Escape Issues

## Problem Description

When developing on Windows, the AIDL compiler generates Java files with headers containing Windows paths (with backslashes). These backslashes can be interpreted as Unicode escape sequences in Java string literals, causing compilation errors when they form illegal Unicode escapes.

The most common error message is:
```
error: illegal unicode escape
```

## Solution

This project includes a comprehensive fix for AIDL-generated Java files with problematic headers. 

The solution works in two parts:

1. **Fixed AIDL Definition**: 
   - The `IRemoteCallback.aidl` interface has been modified to use the `oneway` keyword for more efficient IPC calls.
   - Problematic annotations that might trigger issues have been removed.

2. **Automated Fixing Script**:
   - `fix-aidl.ps1` - A PowerShell script that fixes all AIDL-generated Java files by replacing problematic headers with clean ones.
   - Supports multiple build variants (debug, release, etc.).
   - Uses proper UTF-8 encoding to prevent further encoding issues.

## How to Use

After facing AIDL compilation issues on Windows, simply run:

```powershell
.\fix-aidl.ps1
```

This script will:
1. Scan for generated AIDL Java files across all build variants
2. Replace problematic headers with clean ones
3. Preserve the actual implementation code
4. Verify the fix by attempting to compile the fixed files

## Common AIDL Files That Need Fixing

The most commonly affected files are:
- `IRemoteCallback.java`
- AIDL interfaces in the `provider/remote` package

## When to Run

Run the fix script after:
- Initial project setup on Windows
- Cleaning and rebuilding the project
- Adding new AIDL interfaces
- Getting AIDL-related compilation errors

## Permanent Fix

To avoid running the script repeatedly, consider:
1. Adding it to your build process
2. Using the `oneway` keyword in AIDL interfaces where appropriate
3. Avoiding special characters in file paths 