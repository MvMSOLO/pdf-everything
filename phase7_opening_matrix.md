# Phase 7 opening verification matrix

| Entry path | Platform | Expected source | Persistence behavior |
|---|---|---|---|
| File > Open | Windows | FilePath | normal file path |
| Ctrl+O | Windows | FilePath | normal file path |
| Drag/drop PDF | Windows | FilePath | normal file path |
| Explorer double-click | Windows | startup arg | installer association |
| Open With | Windows | startup arg | installer association |
| Default app | Windows | startup arg | installer association |
| Recent | Windows | FilePath | persistent path + resume metadata |
| Startup `app.exe file.pdf` | Windows | FilePath | immediate open |
| File picker | Android | ContentUri when persistable | persisted grant; otherwise workspace copy |
| ACTION_VIEW | Android | ContentUri or FilePath copy | persisted grant or private workspace |
| ACTION_EDIT | Android | ContentUri or FilePath copy | persisted grant or private workspace |
| ACTION_SEND | Android | ContentUri or FilePath copy | persisted grant or private workspace |
| ACTION_SEND_MULTIPLE | Android | first PDF URI | persisted grant or private workspace |
