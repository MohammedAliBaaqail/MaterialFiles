# This script fixes the AIDL-generated Java files that contain illegal Unicode escapes
# caused by Windows paths with backslashes in header comments.
# The problematic header includes the full aidl.exe command path with Windows paths using backslashes,
# which can cause compilation issues.
# This script replaces the problematic header with a simplified clean header.

$outputDir = "app/build/generated/aidl_source_output_dir/debug/out"
$fixedHeaderSimple = @"
/*
 * This file is auto-generated.  DO NOT MODIFY.
 */

"@

# Helper function to process a directory of files
function Process-Directory {
    param (
        [string]$dirPath,
        [string]$dirName
    )
    
    Write-Host "Processing $dirName directory: $dirPath"
    $files = Get-ChildItem -Path $dirPath -Filter "*.java" -File -ErrorAction SilentlyContinue
    $count = 0

    foreach ($file in $files) {
        Write-Host "Processing file: $($file.FullName)"
        
        # Check if file exists and has content
        if (Test-Path -Path $file.FullName -PathType Leaf) {
            if ((Get-Item $file.FullName).Length -gt 0) {
                $content = Get-Content -Path $file.FullName -Raw
                
                # Find the position of "package"
                $packagePos = $content.IndexOf("package ")
                
                if ($packagePos -gt 0) {
                    # Create new content with simple header + everything after "package"
                    $newContent = $fixedHeaderSimple + $content.Substring($packagePos)
                    
                    # Write the fixed content back to file
                    Set-Content -Path $file.FullName -Value $newContent -NoNewline -Encoding UTF8
                    Write-Host "Fixed file: $($file.FullName)" -ForegroundColor Green
                    $count++
                }
            } else {
                Write-Host "Skipping empty file: $($file.FullName)" -ForegroundColor Yellow
            }
        }
    }

    Write-Host "Fixed $count files in $dirName directory"
    return $count
}

# Process util directory
$utilDir = Join-Path $outputDir "me/zhanghai/android/files/util"
$utilCount = Process-Directory -dirPath $utilDir -dirName "util"

# Process provider directory and its subdirectories
$providerDir = Join-Path $outputDir "me/zhanghai/android/files/provider"
Write-Host "Processing provider directory: $providerDir"

# Process common subdirectory
$commonDir = Join-Path $providerDir "common"
$commonCount = Process-Directory -dirPath $commonDir -dirName "common"

# Process remote subdirectory
$remoteDir = Join-Path $providerDir "remote"
$remoteCount = Process-Directory -dirPath $remoteDir -dirName "remote"

# Check for files directly in the provider directory
$providerRootFiles = Get-ChildItem -Path $providerDir -Filter "*.java" -File -ErrorAction SilentlyContinue | 
                     Where-Object { $_.DirectoryName -eq $providerDir }
$providerRootCount = 0

foreach ($file in $providerRootFiles) {
    # Check if file exists and has content
    if (Test-Path -Path $file.FullName -PathType Leaf) {
        if ((Get-Item $file.FullName).Length -gt 0) {
            $content = Get-Content -Path $file.FullName -Raw
            
            # Find the position of "package"
            $packagePos = $content.IndexOf("package ")
            
            if ($packagePos -gt 0) {
                # Create new content with simple header + everything after "package"
                $newContent = $fixedHeaderSimple + $content.Substring($packagePos)
                
                # Write the fixed content back to file
                Set-Content -Path $file.FullName -Value $newContent -NoNewline -Encoding UTF8
                Write-Host "Fixed file: $($file.FullName)" -ForegroundColor Green
                $providerRootCount++
            }
        } else {
            Write-Host "Skipping empty file: $($file.FullName)" -ForegroundColor Yellow
        }
    }
}

Write-Host "Fixed $providerRootCount files in provider root directory"

$totalFixed = $utilCount + $commonCount + $remoteCount + $providerRootCount
Write-Host ""
Write-Host "Summary:" -ForegroundColor Cyan
Write-Host "--------" -ForegroundColor Cyan
Write-Host "Fixed $utilCount files in util directory"
Write-Host "Fixed $commonCount files in common directory"
Write-Host "Fixed $remoteCount files in remote directory"
Write-Host "Fixed $providerRootCount files in provider root directory"
Write-Host "--------" -ForegroundColor Cyan
Write-Host "Total: Fixed $totalFixed AIDL files" -ForegroundColor Cyan
Write-Host ""
Write-Host "Done!" -ForegroundColor Green 