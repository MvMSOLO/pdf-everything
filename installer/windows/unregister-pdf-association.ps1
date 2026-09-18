$progId = 'PDFEverything.Document.1'
Remove-Item -Path "HKLM:\Software\Classes\$progId" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path 'HKLM:\Software\Classes\Applications\pdf-everything.exe' -Recurse -Force -ErrorAction SilentlyContinue
Remove-ItemProperty -Path 'HKLM:\Software\Classes\.pdf\OpenWithProgids' -Name $progId -ErrorAction SilentlyContinue
Write-Host 'PDF Everything explicit ProgID registration removed. Existing Windows user default is not modified.'
