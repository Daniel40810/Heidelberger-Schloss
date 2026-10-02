# Datenbank (Phase 10)

Die Datenbank ist nie Voraussetzung: Ohne sie läuft das Programm mit den eingebauten Werten, Zustand und Protokoll
liegen dann in Dateien unter `.heidelberg` im Benutzerordner. Mit ihr liegen dort Zustand, Protokoll der Sitzungen,
Blickpunkte, Fahrten, Drehbuch, Quellen, Bauwerke und Chronik. Schema `DEMO`, Dienst `PDBORCL`, Präfix `HEI_`
(wie `SEM_` bei Semiramis).

## Einrichten

1. `db/db.properties.example` nach `db/db.properties` kopieren. Das Passwort leer lassen: Das Startbild fragt danach,
   oder die Umgebungsvariable `HEIDELBERG_DB_PASSWORD` liefert es. Die Datei steht in `.gitignore`.
2. Programm starten (`Heidelberg.bat`, aus dem Projektordner). Im Startbild **Einrichten …** wählen, oder später im
   Bedienfeld unter *Datenbank* **Einrichter ausführen …**.
3. Der Einrichter meldet am Ende `ERGEBNIS: alles in Ordnung`. Sein Protokoll steht in `db/einrichtung.txt`, der
   Bestand des Schemas vorher in `db/bestand.txt` (beide nicht im Git).

Ohne Programm geht es auch: `java -cp "dist/Heidelberg.jar;dist/lib/*" com.dan.heidelberg.db.Einrichter`
oder die Skripte in SQL Developer als Skript (F5) in der Reihenfolge 01 bis 04 ausführen.

## Skripte

| Datei | Inhalt |
|---|---|
| `01_tables.sql` | 11 Tabellen, Schlüssel, Regeln (`_PK`, `_UK`, `_FK`, `_CK`), Kommentare |
| `02_objekte.sql` | Folgen `<TABELLE>_SEQ`, Trigger `<TABELLE>_BI`, Indizes, Geometrie-Metadaten, räumlicher Index `HEI_BUILDING_SX` |
| `03_api.sql` | Paket `HEI_API`: Sitzung beginnen und beenden, Ereignis schreiben, letzten Zustand finden, Aufräumen |
| `04_seed.sql` | Grunddaten, erzeugt aus dem Code (`tools.SeedGen`), nur Einfügen, nie Überschreiben |

Jede Anweisung endet mit einer Zeile `/`. Die Skripte sind wiederholbar: Was es gibt, bleibt unberührt, der Einrichter
meldet es als „schon vorhanden“. Eigene Änderungen an den Grunddaten bleiben stehen.

## Tabellen

| Tabelle | Inhalt |
|---|---|
| `HEI_SOURCE` | Quellen mit Kernaussage, Maßen und Widersprüchen |
| `HEI_STAGE` | die vier Zeitstufen 1619, 1689, 1693, 1764 |
| `HEI_BUILDING` | Bauwerke mit Detailstufe, Rolle, Lage in Metern und als `SDO_GEOMETRY` (WGS84, SRID 8307) |
| `HEI_CHRONIK` | Ereignisse je Zeitstufe |
| `HEI_VIEW` | die 28 Blickpunkte (Drehpunkt, Richtung, Neigung, Abstand) |
| `HEI_CLIP`, `HEI_SHOT`, `HEI_PANEL` | Fahrten und Drehbuch: Clips, Szenen mit Tageszeit und Nebel, Tafeln mit Quelle |
| `HEI_SESSION` | eine Zeile je Programmlauf (Rechner, Fenster, mittlere Bilder je Sekunde, Dauer) |
| `HEI_STATE` | Zustand beim Beenden (`AUTO`, die letzten 10) und von Hand gemerkte Zustände (`MANUELL`) |
| `HEI_EVENT` | Protokoll: Fahrt, Drehbuch, Stufe, Schnitt, Blickpunkt, Standbild, Fehler (90 Tage) |

Das Protokoll hält Ereignisse des Programms fest, keine Eingaben und keine persönlichen Daten außer Rechner- und
Benutzername der Sitzung.

## Passwort

Es steht nie in Skripten, im Quelltext, im Git oder in einem Protokoll. Es kommt aus `db/db.properties`, der
Umgebungsvariablen oder dem Startbild und bleibt nur im Speicher.
