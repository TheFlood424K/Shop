[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Task,

    [string]$Model = "qwen2.5-coder-7b",

    [string]$ApiBase = "http://127.0.0.1:18443/v1",

    [string[]]$Files = @(),

    [int]$MaxFiles = 8,

    [int]$MaxCharsPerFile = 12000,

    [switch]$IncludeGitDiff,

    [switch]$Stream,

    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    $root = [string]((& git rev-parse --show-toplevel 2>$null) -join "`n")

    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($root)) {
        throw "Run this script from inside a Git repository."
    }

    return $root.Trim()
}

function Get-RelativeRepoPath {
    param(
        [Parameter(Mandatory)]
        [string]$RepoRoot,

        [Parameter(Mandatory)]
        [string]$FullPath
    )

    $normalizedRoot = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\', '/')
    $normalizedPath = [System.IO.Path]::GetFullPath($FullPath)

    if (-not $normalizedPath.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the repository: $FullPath"
    }

    return $normalizedPath.Substring($normalizedRoot.Length).TrimStart('\', '/') -replace '\\', '/'
}

function Get-TextFileContent {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [int]$Limit
    )

    try {
        $content = [System.IO.File]::ReadAllText($Path)

        if ($content.Length -gt $Limit) {
            return $content.Substring(0, $Limit) + "`n`n[TRUNCATED]"
        }

        return $content
    }
    catch {
        return "[Could not read file: $($_.Exception.Message)]"
    }
}

function Test-IsExcludedPath {
    param(
        [Parameter(Mandatory)]
        [string]$FullPath,

        [Parameter(Mandatory)]
        [string[]]$ExcludedDirectories
    )

    $pathParts = @($FullPath -split '[\\/]')

    foreach ($part in $pathParts) {
        if ($part -in $ExcludedDirectories) {
            return $true
        }
    }

    return $false
}

function Get-WorkspaceFiles {
    param(
        [Parameter(Mandatory)]
        [string]$Root,

        [Parameter(Mandatory)]
        [int]$Limit
    )

    $extensions = @(
        ".ps1", ".psm1", ".py", ".js", ".ts", ".tsx", ".jsx",
        ".json", ".yml", ".yaml", ".md", ".kt", ".java", ".c",
        ".cpp", ".h", ".hpp", ".cs", ".gradle", ".properties",
        ".xml", ".sh", ".bat"
    )

    $excludedDirectories = @(
        ".git", ".gradle", ".idea", ".vscode", "node_modules",
        "build", "dist", "out", "target", "bin", "obj",
        ".venv", "venv", "vendor", "coverage", ".local-agent"
    )

    $workspaceFiles = @(
        Get-ChildItem -Path $Root -File -Recurse |
            Where-Object {
                $_.Length -le 1MB -and
                $_.Extension -in $extensions -and
                -not (Test-IsExcludedPath -FullPath $_.FullName -ExcludedDirectories $excludedDirectories)
            } |
            Sort-Object Length |
            Select-Object -First $Limit
    )

    return $workspaceFiles
}

function Resolve-AgentFiles {
    param(
        [Parameter(Mandatory)]
        [string]$Root,

        [Parameter(Mandatory)]
        [string[]]$RequestedFiles,

        [Parameter(Mandatory)]
        [int]$Limit
    )

    if ($RequestedFiles.Count -eq 0) {
        return @(Get-WorkspaceFiles -Root $Root -Limit $Limit)
    }

    if ($RequestedFiles.Count -gt $Limit) {
        throw "You supplied $($RequestedFiles.Count) files, but -MaxFiles is $Limit."
    }

    $normalizedRoot = [System.IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $resolved = @()

    foreach ($requestedFile in $RequestedFiles) {
        if ([string]::IsNullOrWhiteSpace($requestedFile)) {
            continue
        }

        $candidatePath = $requestedFile

        if (-not [System.IO.Path]::IsPathRooted($candidatePath)) {
            $candidatePath = Join-Path $Root $candidatePath
        }

        $fullPath = [System.IO.Path]::GetFullPath($candidatePath)

        if (-not $fullPath.StartsWith($normalizedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to read a file outside the repository: $requestedFile"
        }

        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            throw "Requested file does not exist: $requestedFile"
        }

        $fileInfo = Get-Item -LiteralPath $fullPath

        if ($fileInfo.Length -gt 1MB) {
            throw "Requested file exceeds the 1 MB safety limit: $requestedFile"
        }

        $resolved += $fileInfo
    }

    return @($resolved | Sort-Object FullName -Unique)
}

function Invoke-LocalChat {
    param(
        [Parameter(Mandatory)]
        [string]$Endpoint,

        [Parameter(Mandatory)]
        [string]$ModelName,

        [Parameter(Mandatory)]
        [string]$Prompt,

        [switch]$Stream
    )

    $payload = @{
        model = $ModelName
        messages = @(
            @{
                role = "system"
                content = @'
You are a careful local coding agent.

Return ONLY one valid unified Git diff patch.
Do not use Markdown fences.
Do not explain the change.
Do not add prose before or after the patch.
Use paths relative to the repository root and use forward slashes in patch paths.
Do not invent files, source lines, hashes, or context.
Do not alter lock files, dependency versions, CI files, secrets, .env files, generated files, build output, or IDE files.
Make the smallest correct change needed for the requested task.
Only modify files included in the supplied workspace context.
If the supplied context does not support a safe, concrete change, return an empty response.
'@
            },
            @{
                role = "user"
                content = $Prompt
            }
        )
        temperature = 0.1
        stream = [bool]$Stream
    }

    $json = $payload | ConvertTo-Json -Depth 10
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $uri = "$Endpoint/chat/completions"

    if (-not $Stream) {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri $uri `
            -ContentType "application/json; charset=utf-8" `
            -Body $bodyBytes `
            -TimeoutSec 1800

        if ($null -eq $response.choices -or @($response.choices).Count -eq 0) {
            throw "Foundry Local returned no completion choices."
        }

        $text = [string]$response.choices[0].message.content

        if ([string]::IsNullOrWhiteSpace($text)) {
            throw "Foundry Local returned an empty response."
        }

        return $text.Trim()
    }

    Write-Host ""
    Write-Host "Streaming model output:" -ForegroundColor Cyan
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, $uri)
    $request.Headers.Accept.ParseAdd("text/event-stream")
    $request.Content = [System.Net.Http.ByteArrayContent]::new($bodyBytes)
    $request.Content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/json; charset=utf-8")

    $response = $null
    $reader = $null
    $output = [System.Text.StringBuilder]::new()

    try {
        $response = $client.SendAsync(
            $request,
            [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
        ).GetAwaiter().GetResult()

        if (-not $response.IsSuccessStatusCode) {
            $errorText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            throw "Foundry Local returned HTTP $([int]$response.StatusCode): $errorText"
        }

        $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)

        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()

            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }

            if (-not $line.StartsWith("data:")) {
                continue
            }

            $data = $line.Substring(5).Trim()

            if ($data -eq "[DONE]") {
                break
            }

            try {
                $chunk = $data | ConvertFrom-Json
                $choices = @($chunk.choices)

                if ($choices.Count -eq 0) {
                    continue
                }

                $delta = $choices[0].delta
                $content = ""

                if ($null -ne $delta -and $null -ne $delta.content) {
                    $content = [string]$delta.content
                }

                if (-not [string]::IsNullOrEmpty($content)) {
                    Write-Host -NoNewline $content
                    [void]$output.Append($content)
                }
            }
            catch {
                Write-Host ""
                Write-Host "Warning: Could not parse one stream chunk: $($_.Exception.Message)" -ForegroundColor Yellow
            }
        }
    }
    finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }

        if ($null -ne $response) {
            $response.Dispose()
        }

        $request.Dispose()
        $client.Dispose()
        $handler.Dispose()
    }

    Write-Host ""
    Write-Host "------------------------------------------------------------" -ForegroundColor DarkGray

    $text = $output.ToString().Trim()

    if ([string]::IsNullOrWhiteSpace($text)) {
        throw "Foundry Local returned an empty streamed response."
    }

    return $text
}

function Remove-MarkdownFences {
    param(
        [Parameter(Mandatory)]
        [string]$Text
    )

    $clean = $Text.Trim()

    $clean = [regex]::Replace(
        $clean,
        '^\s*```(?:diff|patch|git|text)?\s*\r?\n',
        '',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )

    $clean = [regex]::Replace($clean, '\r?\n\s*```\s*$', '')

    return $clean.Trim()
}

function Test-PatchTargets {
    param(
        [Parameter(Mandatory)]
        [string]$Patch,

        [Parameter(Mandatory)]
        [string[]]$AllowedPaths
    )

    $blockedPrefixes = @(
        ".env",
        ".github/",
        ".git/",
        ".idea/",
        ".vscode/",
        ".local-agent/",
        "node_modules/",
        "build/",
        "dist/",
        "out/",
        "target/",
        "bin/",
        "obj/"
    )

    $blockedNames = @(
        "package-lock.json",
        "npm-shrinkwrap.json",
        "yarn.lock",
        "pnpm-lock.yaml",
        "gradle.lockfile",
        "settings.gradle",
        "settings.gradle.kts"
    )

    $patchPaths = @()

    foreach ($match in [regex]::Matches($Patch, '(?m)^(?:---|\+\+\+) [ab]/(.+)$')) {
        $path = $match.Groups[1].Value.Trim()

        if ($path -and $path -ne "dev/null") {
            $patchPaths += $path
        }
    }

    $patchPaths = @($patchPaths | Sort-Object -Unique)

    if ($patchPaths.Count -eq 0) {
        throw "Patch has no recognizable file paths."
    }

    foreach ($path in $patchPaths) {
        $normalized = $path -replace '\\', '/'

        if ($normalized -match '(^|/)\.\.(/|$)') {
            throw "Refusing patch with parent-directory traversal: $normalized"
        }

        foreach ($prefix in $blockedPrefixes) {
            if ($normalized -eq $prefix.TrimEnd('/') -or $normalized.StartsWith($prefix)) {
                throw "Refusing patch that targets a protected path: $normalized"
            }
        }

        foreach ($name in $blockedNames) {
            if ($normalized -eq $name -or $normalized.EndsWith("/$name")) {
                throw "Refusing patch that targets a protected file: $normalized"
            }
        }

        if ($normalized -notin $AllowedPaths) {
            throw "Refusing patch for a file not supplied to the model: $normalized"
        }
    }
}

function Test-MeaningfulPatch {
    param(
        [Parameter(Mandatory)]
        [string]$Patch
    )

    $removedLines = @(
        [regex]::Matches($Patch, '(?m)^-(?!---)(.*)$') |
            ForEach-Object { $_.Groups[1].Value }
    )

    $addedLines = @(
        [regex]::Matches($Patch, '(?m)^\+(?!\+\+\+)(.*)$') |
            ForEach-Object { $_.Groups[1].Value }
    )

    if ($removedLines.Count -eq 0 -or $addedLines.Count -eq 0) {
        return $false
    }

    $removedText = [string]($removedLines -join "`n")
    $addedText = [string]($addedLines -join "`n")

    return $removedText -ne $addedText
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot

$agentDir = Join-Path $repoRoot ".local-agent"
New-Item -ItemType Directory -Force $agentDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$patchPath = Join-Path $agentDir "proposal-$timestamp.patch"
$promptPath = Join-Path $agentDir "prompt-$timestamp.txt"

$status = [string]((& git status --short) -join "`n")
$branch = [string]((& git branch --show-current) -join "`n")
$recentLog = [string]((& git log --oneline -5) -join "`n")

$gitDiff = ""

if ($IncludeGitDiff) {
    $gitDiff = [string]((& git diff --no-ext-diff --unified=3) -join "`n")

    if ($gitDiff.Length -gt 20000) {
        $gitDiff = $gitDiff.Substring(0, 20000) + "`n`n[DIFF TRUNCATED]"
    }
}

$workspaceFiles = @(Resolve-AgentFiles -Root $repoRoot -RequestedFiles $Files -Limit $MaxFiles)

if ($workspaceFiles.Count -eq 0) {
    throw "No readable workspace files were selected."
}

$allowedPaths = @()
$fileContext = @()

foreach ($file in $workspaceFiles) {
    $relativePath = Get-RelativeRepoPath -RepoRoot $repoRoot -FullPath $file.FullName
    $allowedPaths += $relativePath

    $content = Get-TextFileContent -Path $file.FullName -Limit $MaxCharsPerFile

    $fileContext += @"
===== FILE: $relativePath =====
$content
===== END FILE: $relativePath =====
"@
}

$prompt = @"
Repository branch:
$branch

Current Git status:
$status

Recent commits:
$recentLog

Task:
$Task

Existing uncommitted diff:
$gitDiff

Files supplied to the model:
$($allowedPaths -join "`n")

Workspace context:
$($fileContext -join "`n")
"@

Set-Content -Path $promptPath -Value $prompt -Encoding utf8

Write-Host ""
Write-Host "Files supplied to model:" -ForegroundColor Cyan
$allowedPaths | ForEach-Object { Write-Host " - $_" }

Write-Host ""
Write-Host "Requesting a patch from Foundry Local..." -ForegroundColor Cyan

$patch = Invoke-LocalChat `
    -Endpoint $ApiBase `
    -ModelName $Model `
    -Prompt $prompt `
    -Stream:$Stream

$patch = Remove-MarkdownFences -Text $patch

if (-not ($patch -match '(?m)^diff --git ')) {
    Set-Content -Path $patchPath -Value $patch -Encoding utf8

    Write-Host ""
    Write-Host "The model did not return a valid Git patch. It was NOT applied." -ForegroundColor Yellow
    Write-Host "Raw response saved:" -ForegroundColor Yellow
    Write-Host $patchPath -ForegroundColor Yellow
    exit 2
}

if (-not (Test-MeaningfulPatch -Patch $patch)) {
    Set-Content -Path $patchPath -Value $patch -Encoding utf8

    Write-Host ""
    Write-Host "The model returned a no-op or malformed patch. It was NOT applied." -ForegroundColor Yellow
    Write-Host "Raw response saved:" -ForegroundColor Yellow
    Write-Host $patchPath -ForegroundColor Yellow
    exit 2
}

Test-PatchTargets -Patch $patch -AllowedPaths $allowedPaths

Set-Content -Path $patchPath -Value $patch -Encoding utf8

Write-Host ""
Write-Host "Patch saved:" -ForegroundColor Green
Write-Host $patchPath -ForegroundColor Green

Write-Host ""
Write-Host "Patch statistics:" -ForegroundColor Cyan
& git apply --stat $patchPath

Write-Host ""
Write-Host "Patch preview:" -ForegroundColor Cyan
Get-Content -Path $patchPath

& git apply --check $patchPath

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Git rejected this patch. It was NOT applied." -ForegroundColor Yellow
    exit 3
}

Write-Host ""
Write-Host "Git validation passed. The patch has NOT been applied." -ForegroundColor Green
Write-Host "Inspect with: code `"$patchPath`"" -ForegroundColor DarkGray

if ($Apply) {
    $confirmation = Read-Host "Apply this validated patch to the current branch? Type APPLY to continue"

    if ($confirmation -eq "APPLY") {
        & git apply $patchPath

        if ($LASTEXITCODE -ne 0) {
            throw "Git failed while applying the patch. Review the patch and repository state."
        }

        Write-Host ""
        Write-Host "Patch applied successfully." -ForegroundColor Green
        Write-Host "Review changes with: git diff" -ForegroundColor Green
    }
    else {
        Write-Host ""
        Write-Host "Patch was not applied." -ForegroundColor Yellow
    }
}