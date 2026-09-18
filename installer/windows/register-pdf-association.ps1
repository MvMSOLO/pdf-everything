# PDF Everything - optional per-machine association registration helper.
# The official installer already registers the PDF capability through jpackage --file-associations.
# This script exists for enterprise/custom MSI pipelines that need an explicit application ProgID.
param(
    [Parameter(Mandatory=$true)][string]$InstallDir
)
$exe = Join-Path $InstallDir 'pdf-everything.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw "PDF Everything executable not found: $exe" }
$progId = 'PDFEverything.Document.1'
$classes = 'HKLM:\Software\Classes'
New-Item -Path "$classes\$progId" -Force | Out-Null
New-ItemProperty -Path "$classes\$progId" -Name '(Default)' -Value 'PDF Everything PDF Document' -PropertyType String -Force | Out-Null
New-Item -Path "$classes\$progId\DefaultIcon" -Force | Out-Null
New-ItemProperty -Path "$classes\$progId\DefaultIcon" -Name '(Default)' -Value "`"$exe`",0" -PropertyType String -Force | Out-Null
New-Item -Path "$classes\$progId\shell\open\command" -Force | Out-Null
New-ItemProperty -Path "$classes\$progId\shell\open\command" -Name '(Default)' -Value "`"$exe`" `"%1`"" -PropertyType String -Force | Out-Null
New-Item -Path "$classes\Applications\pdf-everything.exe\shell\open\command" -Force | Out-Null
New-ItemProperty -Path "$classes\Applications\pdf-everything.exe\shell\open\command" -Name '(Default)' -Value "`"$exe`" `"%1`"" -PropertyType String -Force | Out-Null
New-Item -Path "$classes\.pdf\OpenWithProgids" -Force | Out-Null
New-ItemProperty -Path "$classes\.pdf\OpenWithProgids" -Name $progId -Value ([byte[]]@()) -PropertyType Binary -Force | Out-Null
New-ItemProperty -Path "$classes\PDFEverything.Document.1" -Name 'PerceivedType' -Value 'document' -PropertyType String -Force | Out-Null
Write-Host 'PDF Everything PDF capability registered. Windows default choice remains user-controlled.'
