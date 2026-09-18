# PDF Everything — PDF Engine Strategy & Licensing Gate

This document is the source-of-truth for the engine boundary and distribution gate.

## Selected adapters

### Windows / Desktop — Apache PDFBox 3.0.8
- License: Apache License 2.0.
- Current upstream release as of 2026-09-16: PDFBox 3.0.8.
- JVM/Windows support: Java library; this project packages it behind the desktop adapter.
- Parsing/rendering/text/annotation/form/outline/write capabilities are exposed through `PdfEngine` rather than leaking PDFBox types into common UI code.
- Incremental serialization is exposed and followed by independent reopen validation.
- PDFBox supports PDF creation/manipulation, text extraction, forms and rendering; malformed input errors are surfaced rather than swallowed.

### Android — PdfBox-Android 2.0.27.0
- License: Apache License 2.0.
- The TomRoush port is based on PDFBox 2.0.27 and documents Android API 19+ as its baseline; PDF Everything's current app minSdk is 28.
- The Android adapter is isolated behind `PdfEngine`.
- Because this port tracks an older PDFBox line, capabilities are reported honestly: desktop has the broader write/incremental-save gate; Android's adapter exposes only the write operations that are implemented and independently verifiable in this project.

## Capability matrix

| Capability | Desktop PDFBox 3.0.8 | Android PdfBox-Android 2.0.27.0 | Contract |
|---|---:|---:|---|
| Parse/open | Yes | Yes | Native engine |
| Page render | Yes | Yes | Viewport-aware, async caller, bounded cache |
| Text extraction/search coordinates | Yes | Yes | Indexed in adapter |
| Image/render transparency/font fidelity | Engine-supported | Engine-supported | Preserve native PDF where renderer supports it |
| Existing annotations | Yes | Yes | Native model import |
| AcroForm | Yes | Yes | Native field inspection/fill where supported |
| Outline/bookmarks | Yes | Yes | Native import; desktop rebuild/write supported |
| Native metadata write | Yes | Adapter path available | Actual PDF metadata, never UI-only |
| Actual object insertion | Yes | Limited | Only operations with native PDFBox mapping are accepted |
| Existing text structural editing | Explicitly guarded | Explicitly guarded | Unsupported when no stable content-stream object identity exists; no white-overpaint fake |
| Full save | Yes | Yes | Temp-file write -> reopen -> validate -> replace |
| Incremental save | Yes | Adapter exposes only where runtime supports it | Never claims success without reopen verification |
| Export | Yes | Yes | Uses native PDF serialization |
| Validate | Structural + reopen validation | Structural + reopen validation | Independent artifact verification |
| Optimize | Native reserialization/normalization preparation | Normalization preparation | Never raster-flattens |
| Encrypted/password PDFs | Detect; password-required error path | Detect; password-required error path | No silent failure |
| Malformed PDFs | Errors surface | Errors surface | No crash masking |
| ARM64 Android | Release ABI gate required | Release ABI gate required | Not claimed by source alone |

## Distribution gate

Apache PDFBox is Apache-2.0 and the upstream project publishes its current release under that license. Before release, include the applicable Apache `LICENSE`/`NOTICE` material for redistributed binaries. PDFBox also contains cryptographic functionality; downstream distributions must comply with applicable export-control requirements.

The Android port also declares Apache-2.0 for its main code and requires its Apache license/notice material to remain with redistribution.

## Non-negotiable editing rule

The adapter must never convert structural editing into screenshot replacement, a white rectangle plus replacement text, or another UI-only overlay. When a native PDF object cannot be addressed safely, `getObjectEditability()` reports `READ_ONLY`/`UNSUPPORTED` and mutation APIs throw a precise `UnsupportedOperationException`.

## Verification pipeline

```text
Engine mutation
  -> transaction boundary
  -> native PDF object mutation
  -> serialize to temp artifact
  -> independently reopen temp artifact
  -> validate pages/boxes/basic structure
  -> atomic replacement
  -> report bytes + validation
```

## Current release verification

Apache's release page lists PDFBox 3.0.8 as the current 3.0.x feature release. Apache's security page lists 3.0.8 as the fixed release for the recent 3.0.x path-traversal issue affecting the example extractor. The issue is in the example code path, but using the current release avoids depending on an older 3.0.x artifact.

References:
- https://pdfbox.apache.org/download
- https://pdfbox.apache.org/security.html
- https://pdfbox.apache.org/faq.html
- https://github.com/TomRoush/PdfBox-Android
