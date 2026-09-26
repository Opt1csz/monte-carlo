param(
    [Parameter(Mandatory=$true)]
    [string]$StatePath,
    [string]$Height = "resources",
    [int]$MaxAgents = 1500
)

$ErrorActionPreference = "Stop"

python -c "import matplotlib, pandas, numpy; print('Python viewer dependencies found.')"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Installing viewer dependencies..."
    python -m pip install -r (Join-Path $PSScriptRoot "requirements.txt")
}

python (Join-Path $PSScriptRoot "viewer.py") `
    --state $StatePath `
    --height $Height `
    --max-agents $MaxAgents
