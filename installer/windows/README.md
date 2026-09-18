# Windows PDF association

The primary installer path uses Compose Multiplatform/jpackage `fileAssociation(mimeType="application/pdf", extension="pdf", ...)`, which is passed to `jpackage --file-associations` during native package creation. jpackage then installs the file association alongside the application.

`register-pdf-association.ps1` is an enterprise/custom-MSI helper for deployments that require an explicit stable ProgID (`PDFEverything.Document.1`). It does **not** write Windows `UserChoice`, so it cannot silently steal the user's default app. The app itself opens Windows Default Apps settings for the user-driven default selection.
