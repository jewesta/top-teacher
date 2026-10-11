# System Settings

## Settings structure

`Einstellungen` contains the top-level tabs `Fächer`, `Notenschlüssel`, and
`Systemeinstellungen`. Backup and database reset are no longer separate
top-level tabs.

`Systemeinstellungen` uses a fixed master/detail layout:

- the grid on the left lists `Dateizugriff`, `Sicherung`, and `Zurücksetzen`;
- the list has no quick filter or action bar and always keeps exactly one
  section selected;
- `Dateizugriff` is initially selected;
- the selected section is shown directly in the context area on the right,
  without a secondary tab bar;
- section components remain alive while the user switches sections, so
  unsaved form state is retained; and
- actions belonging to a section appear in an action bar at the top of its
  context content. The existing reset presentation remains the deliberate
  exception because its destructive action stays below the explanation.

Additional system settings are added as sections rather than as new top-level
settings tabs.

## File access

The first control in the `Dateizugriff` action bar is the `Installationsort`
dropdown with the choices `Lokal` and `Entfernt`. The selected location is
persisted with `Speichern`; changing it immediately updates the section's
explanation and mapping controls. A new installation defaults to `Lokal`.
Existing installations with saved mappings initially use `Entfernt` unless a
location has already been saved.

### Local installation

`Lokal` means TopTeacher and the AI use the same system. The explanation is:

> TopTeacher! läuft auf demselben System wie die KI und geht davon aus, dass alle Dateipfade, die es bekommt, lokal erreichbar sind.

`Zuordnung hinzufügen` and the complete mapping editor are hidden. Mappings
cannot be defined in this mode, and the backend resolves incoming absolute
file paths directly without applying mappings. The target must still be an
existing, readable regular file. Relative paths are rejected.

Previously stored mappings remain available when switching back to `Entfernt`
but are ignored while `Lokal` is saved. Unsaved mapping drafts are retained when
switching between the two choices; invalid or unavailable remote paths do not
prevent saving `Lokal`.

### Remote installation

`Entfernt` maps an absolute shared-folder root visible on a client to the
absolute root of the same shared folder on the TopTeacher server. For example:

| Client path | TopTeacher server path |
| --- | --- |
| `/Volumes/School` | `/mnt/school` |

Each row contains one client root and one server root. The section action bar
offers `Zuordnung hinzufügen` and `Speichern`. Removing a row changes only the
form until the complete list is saved. Up to 50 mappings can be stored. The
location and mapping list are saved together.

Client roots must be absolute and unique. They do not have to exist on the
server. Server roots must be absolute, reachable, readable directories when
the settings are saved. Save is available only while the location or complete
mapping list has changed and the visible mappings are valid.

When TopTeacher receives an absolute client file path, it selects the most
specific matching client root, appends the relative path to the configured
server root, and resolves both locations on the server. The resulting target
must be a readable regular file inside the real server root. This prevents
parent traversal and symbolic links from escaping the configured share.

The mapping grants no new operating-system permissions and does not copy or
upload the file. TopTeacher can read only files already accessible to its
server process through the configured mounted share.

## MCP image output

Stored exam material images can be converted directly to native MCP image
content. TopTeacher keeps the stored media type and Base64-encodes the stored
image bytes as the `data` value. The resulting MCP content has this shape:

```json
{
  "type": "image",
  "mimeType": "image/png",
  "data": "<base64>"
}
```

This conversion is a reusable prerequisite for exam-oriented MCP tools; it
does not by itself introduce a new public MCP tool.
