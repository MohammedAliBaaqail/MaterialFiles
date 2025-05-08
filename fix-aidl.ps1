# Comprehensive AIDL Fix Script
# This script fixes AIDL-generated Java files that contain illegal Unicode escapes
# caused by Windows paths with backslashes in header comments.

# Define possible build variants to check
$buildVariants = @(
    "debug", 
    "release",
    "debugAndroidTest",
    "releaseAndroidTest"
)

$fixedHeaderSimple = @"
/*
 * This file is auto-generated.  DO NOT MODIFY.
 */

"@

$totalFixedCount = 0

# Helper function to process a directory of Java files
function Process-Directory {
    param (
        [string]$dirPath,
        [string]$dirName
    )
    
    if (-not (Test-Path -Path $dirPath)) {
        Write-Host "Directory not found: $dirPath" -ForegroundColor Yellow
        return 0
    }
    
    Write-Host "Processing $dirName directory: $dirPath"
    $files = Get-ChildItem -Path $dirPath -Filter "*.java" -File -ErrorAction SilentlyContinue
    $count = 0

    foreach ($file in $files) {
        # Check if file exists and has content
        if ((Test-Path -Path $file.FullName -PathType Leaf) -and ((Get-Item $file.FullName).Length -gt 0)) {
            try {
                $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
                
                # Find the position of "package"
                $packagePos = $content.IndexOf("package ")
                
                if ($packagePos -gt 0) {
                    # Create new content with simple header + everything after "package"
                    $newContent = $fixedHeaderSimple + $content.Substring($packagePos)
                    
                    # Write the fixed content back to file with UTF-8 encoding
                    [System.IO.File]::WriteAllText($file.FullName, $newContent, [System.Text.Encoding]::UTF8)
                    
                    Write-Host "Fixed file: $($file.FullName)" -ForegroundColor Green
                    $count++
                }
            }
            catch {
                Write-Host "Error processing file $($file.FullName): $_" -ForegroundColor Red
            }
        } else {
            Write-Host "Skipping empty or non-existent file: $($file.FullName)" -ForegroundColor Yellow
        }
    }

    Write-Host "Fixed $count files in $dirName directory"
    return $count
}

foreach ($variant in $buildVariants) {
    $outputDir = "app/build/generated/aidl_source_output_dir/$variant/out"
    
    if (-not (Test-Path -Path $outputDir)) {
        Write-Host "Skipping non-existent build variant directory: $outputDir" -ForegroundColor Yellow
        continue
    }
    
    Write-Host "Processing build variant: $variant" -ForegroundColor Cyan
    Write-Host "-----------------------------------" -ForegroundColor Cyan
    
    # Process util directory (contains IRemoteCallback)
    $utilDir = Join-Path $outputDir "me/zhanghai/android/files/util"
    $utilCount = Process-Directory -dirPath $utilDir -dirName "util"
    
    # Process provider directory and its subdirectories
    $providerDir = Join-Path $outputDir "me/zhanghai/android/files/provider"
    
    # Process common subdirectory
    $commonDir = Join-Path $providerDir "common"
    $commonCount = Process-Directory -dirPath $commonDir -dirName "common"
    
    # Process remote subdirectory
    $remoteDir = Join-Path $providerDir "remote"
    $remoteCount = Process-Directory -dirPath $remoteDir -dirName "remote"
    
    # Check for files directly in the provider directory
    $providerCount = 0
    if (Test-Path -Path $providerDir) {
        $providerFiles = Get-ChildItem -Path $providerDir -Filter "*.java" -File -ErrorAction SilentlyContinue | 
                         Where-Object { $_.DirectoryName -eq $providerDir }
        
        foreach ($file in $providerFiles) {
            if ((Test-Path -Path $file.FullName -PathType Leaf) -and ((Get-Item $file.FullName).Length -gt 0)) {
                try {
                    $content = Get-Content -Path $file.FullName -Raw -Encoding UTF8
                    
                    # Find the position of "package"
                    $packagePos = $content.IndexOf("package ")
                    
                    if ($packagePos -gt 0) {
                        # Create new content with simple header + everything after "package"
                        $newContent = $fixedHeaderSimple + $content.Substring($packagePos)
                        
                        # Write the fixed content back to file with UTF-8 encoding 
                        [System.IO.File]::WriteAllText($file.FullName, $newContent, [System.Text.Encoding]::UTF8)
                        
                        Write-Host "Fixed file: $($file.FullName)" -ForegroundColor Green
                        $providerCount++
                    }
                }
                catch {
                    Write-Host "Error processing file $($file.FullName): $_" -ForegroundColor Red
                }
            }
        }
        
        Write-Host "Fixed $providerCount files in provider root directory"
    }
    
    # Calculate total for this variant
    $variantTotal = $utilCount + $commonCount + $remoteCount + $providerCount
    $totalFixedCount += $variantTotal
    
    Write-Host ""
    Write-Host "Summary for $variant:" -ForegroundColor Cyan
    Write-Host "--------" -ForegroundColor Cyan
    Write-Host "Fixed $utilCount files in util directory"
    Write-Host "Fixed $commonCount files in common directory"
    Write-Host "Fixed $remoteCount files in remote directory"
    Write-Host "Fixed $providerCount files in provider root directory"
    Write-Host "--------" -ForegroundColor Cyan
    Write-Host "Total: Fixed $variantTotal AIDL files in $variant variant" -ForegroundColor Cyan
    Write-Host ""
}

# Overall summary
Write-Host "OVERALL SUMMARY:" -ForegroundColor Cyan
Write-Host "====================" -ForegroundColor Cyan
Write-Host "Total fixed files across all variants: $totalFixedCount" -ForegroundColor Green
Write-Host ""

# Attempt to compile to verify the fixes worked
if ($totalFixedCount -gt 0) {
    Write-Host "Running Gradle to test if the fixes worked..."
    & ./gradlew compileDebugJavaWithJavac
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Compilation successful! The fixes worked." -ForegroundColor Green
    } else {
        Write-Host "Compilation failed. Some files may still have issues." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "No files were fixed. Make sure you've run the AIDL generation task first." -ForegroundColor Yellow
}

Write-Host "Done!" -ForegroundColor Green 