# System Settings

## Settings structure

`Einstellungen` contains the top-level tabs `Fächer`, `Notenschlüssel`, and
`Systemeinstellungen`. Backup and database reset are no longer separate
top-level tabs.

`Systemeinstellungen` uses a fixed master/detail layout:

- the grid on the left lists `MCP > Dateibehandlung`, `MCP > Sicherheit`,
  `Sicherung`, and `Zurücksetzen`;
- the list has no quick filter or action bar and always keeps exactly one
  section selected;
- `MCP > Dateibehandlung` is initially selected;
- the selected section is shown directly in the context area on the right,
  without a secondary tab bar;
- section components remain alive while the user switches sections, so
  unsaved form state is retained; and
- actions belonging to a section appear in an action bar at the top of its
  context content. The existing reset presentation remains the deliberate
  exception because its destructive action stays below the explanation.

Additional system settings are added as sections rather than as new top-level
settings tabs.

## MCP access and pupil pseudonymization

`MCP > Sicherheit` maintains one named access token per client or purpose.
Each line contains a designation, access level, optional expiry date, and its
own save/create and revoke actions. Creation defaults to `Pseudonymisiert`.
The metadata line shows creation, last use, and expired/revoked status.

`Token hinzufügen` adds an unsaved row. `Anlegen...` creates its credential and
opens a one-time copy dialog; the credential is discarded from that dialog
after it closes. `Speichern` is available only for valid changed rows and
does not save other rows. `Token widerrufen...` asks for confirmation and
revokes that token immediately; its row remains visible as read-only history.
Drafts survive section switching and actions on other rows. Saves compare the
configuration originally loaded by the row with the current database record;
a stale form cannot overwrite another session's access-level change. A conflict
preserves the draft and offers an explicit reload of the current configuration.

The expiry date includes the whole selected day in the server's local timezone.
Changing only a token's designation or access level preserves its precise
existing expiry timestamp. An expired token must have its expiry extended or
cleared before metadata can be saved.

Both MCP settings sections share the same line-item layout. Row actions align
with the input controls and retain their vertical position when validation
messages or helper text appear.

Token records are persisted in the database and distinguish
`Vollzugriff` from `Pseudonymisiert`. Credential storage must keep a one-way
verifier rather than the reusable token itself; a newly generated credential
is shown once for configuring the client.

### Pseudonymized pupil identity

A pseudonymized token protects pupil names while retaining normal academic
operations. TopTeacher may expose the stable pupil ID, surname-first initials
(for example, `Müller, Hans` becomes `MH`), class context such as `Q1` or `EF`,
and other existing non-name domain data. This is pseudonymization, not a claim
of complete anonymity.

The pupil ID is the authoritative identifier. Initials need not be unique and
are never expanded with additional name letters to resolve collisions. The
AI can ask which `MH` the user means using class/course context; if that is
still ambiguous, it must distinguish candidates by ID rather than guess.

TopTeacher must suppress names obtained from pupil records throughout the MCP
exchange: structured fields, generated messages, confirmation/elicitation
text, errors, metadata, filenames, and generated document content. The same
pupil must be represented consistently in pupil lists, course/exam rosters,
results, and operation responses. The shared `PupilNamePseudonymizer` is the
mandatory boundary for every pupil name entering these MCP responses. Existing
response schemas are retained: under pseudonymized access, `name` contains the
surname-first initials and `surname` is empty.

Pseudonymized discovery and matching use IDs or the displayed pseudonyms and
context. Real-name search or matching is unavailable, including queries that
would reveal whether a guessed name exists. Pupil names cannot be recovered by
calling a different tool or requesting a different response format.

Pupil creation, import, and renaming are unavailable, including indirect
creation through course-roster or assignment tools. Existing course creation
and assignment tools accept `pupilIds` for the name-free path. Do not combine
IDs with a named roster. Omitting the roster or supplying an empty ID list
creates an empty course; assigning to an existing course requires a nonempty
ID list. These paths never create or rename pupils.

All other operations remain available under their existing domain safeguards,
including course/exam assignments and removals, result reading and entry, and
exam, material, and level-of-expectations work. The access level does not make
these operations read-only or bypass existing archive, validation, and lock
rules. Permissions are enforced by TopTeacher for every operation.

Exports remain available when names inserted by TopTeacher from pupil records
are replaced with pseudonyms, including in filenames and metadata. The existing
server-side database backup operation remains available because it returns
only the backup filename, not its contents. Transmitting raw database contents
would expose stored pupil names and cannot be offered through a pseudonymized
token.

### User content and AI interaction protocol

Arbitrary user-authored content remains available, including comments, exam
and EH text, images, and uploaded pupil work. TopTeacher does not promise
automatic name detection or removal in that content. Users must remove or
cover names before sharing such content with the AI, whether directly in chat
or through TopTeacher material accessible to MCP.

The AI interaction protocol must state that pupils are addressed by initials
and IDs, ambiguous references require clarification using class/course context
or IDs, and real names must not be requested or reconstructed. The settings
explanation must describe the user-content boundary using `Du`.

### Authentication and migration

Every MCP HTTP request authenticates against current token records. Revocation,
expiry, and saved access-level changes apply to subsequent requests without an
application restart. Sessions belong to their creating token and access level;
another token cannot use or replay that session. After an access-level change,
the client must initialize a new session. Missing authentication or missing
permission context never defaults to full access. MCP responses cannot be
cached.

MCP remains disabled by default through `tt.mcp.enabled`. Tokens can be
maintained in the UI while the endpoint is disabled. Enabling MCP without any
active token keeps the endpoint inaccessible until a token is created.

On the first enabled startup, an existing `tt.mcp.token-file` is imported once
as a full-access token named `Bisheriger MCP-Zugriff`. If no file is configured,
the migration is marked complete without creating a token. Subsequent startups
use only database credentials and do not reopen the legacy file. The old file
can be deleted and the property removed after import. Revoking the imported
token never causes it to reappear on restart. Token configuration and the
migration marker survive empty/demo dataset initialization and database reset.

## File access

The first control in the `MCP > Dateibehandlung` action bar is the `Installationsort`
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
