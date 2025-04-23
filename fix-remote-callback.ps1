# Simple script to fix the IRemoteCallback.java file which is causing Unicode escape errors

$remoteCallbackPath = "app/build/generated/aidl_source_output_dir/debug/out/me/zhanghai/android/files/util/IRemoteCallback.java"

Write-Host "Trying to fix $remoteCallbackPath..."

if (!(Test-Path -Path $remoteCallbackPath)) {
    Write-Host "File not found: $remoteCallbackPath"
    exit 1
}

# Read the file content
$content = Get-Content -Path $remoteCallbackPath -Raw

# Replace the problematic header
$simpleHeader = @"
/*
 * This file is auto-generated.  DO NOT MODIFY.
 */

"@

# Find the position of "package"
$packagePos = $content.IndexOf("package ")

if ($packagePos -gt 0) {
    # Create new content with simple header + everything after "package"
    $newContent = $simpleHeader + $content.Substring($packagePos)
    
    # Write the fixed content back to file
    Set-Content -Path $remoteCallbackPath -Value $newContent -NoNewline
    
    Write-Host "Successfully fixed header in $remoteCallbackPath"
} else {
    Write-Host "Failed to find package statement in $remoteCallbackPath"
    exit 1
}

# Try to compile to see if it worked
Write-Host "Running Gradle to test if the fix worked..."
& ./gradlew compileDebugJavaWithJavac

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful! The fix worked."
} else {
    Write-Host "Compilation failed. The fix did not work."
    exit 1
}

Write-Host "Done!" 