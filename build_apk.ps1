$gradleVersion = "8.4"
$gradleUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
$destDir = "$PSScriptRoot\gradle_dist"
$zipPath = "$destDir\gradle.zip"

Write-Host "Setting up build environment..."

if (-not (Test-Path $destDir)) {
    New-Item -ItemType Directory -Path $destDir | Out-Null
}

if (-not (Test-Path "$destDir\gradle-$gradleVersion")) {
    Write-Host "Downloading Gradle $gradleVersion..."
    Invoke-WebRequest -Uri $gradleUrl -OutFile $zipPath
    
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $destDir -Force
    
    Remove-Item $zipPath
} else {
    Write-Host "Gradle already present."
}

$gradleBin = "$destDir\gradle-$gradleVersion\bin\gradle.bat"
Write-Host "Using Gradle at: $gradleBin"

# Run Build
Write-Host "Starting Build..."
& $gradleBin assembleDebug --stacktrace
