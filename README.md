# Scholar Illustrations

Scholar Illustrations is an independent addon for [Scholar](https://www.curseforge.com/minecraft/mc-mods/scholar) that adds drawing and PNG image placement to book pages. It was developed for illustrated journals, notes, and other in-game books.

## Features

- Draw on either page using configurable brush sizes and colors, an eraser, and undo/redo.
- Import PNG images, position them, and resize them on book pages.
- Use HQ mode to retain detail in imported images when resizing.
- Render illustrations above or below book text.
- Show or hide the illustration controls in Scholar's editor.
- Synchronize and store illustrations on multiplayer servers.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.250 or newer in the 21.1 series
- Scholar 1.2.10 (required, installed separately)
- Java 21

Install Scholar Illustrations on both the server and participating clients. Scholar is not bundled with this addon.

The tested binary may be distributed as `scholar-illustrations-1.21.1-0.2.1.jar`. Its embedded version remains `0.2.1-dev`; renaming the JAR does not change the internal version or binary contents.

## Usage

Open a writable book in Scholar. Use the pencil button to show the illustration controls, then select **Draw L** or **Draw R** to work on a page. The drawing editor provides brush, eraser, and image import tools.

Imported images can be positioned and resized. HQ mode retains the source PNG data, while standard mode imports into the drawing canvas. Use **Above text / Below text** to control how illustrations are layered with the page's writing. Save the book to preserve the changes.

## Known limitations

- Illustrations are associated with page numbers. Inserting or deleting pages may not move illustrations with the surrounding text.
- A page can contain one HQ PNG image in addition to its drawing layer.
- Text does not automatically wrap around illustrations.
- The addon supports up to 16 illustrated pages per book.
- Older development builds may not understand newer artwork metadata; update server and clients together.

## Server data and backups

Illustration data is stored in the world under `data/scholar_illustrations/`. Include this directory when backing up the world.

## Building

This archive contains the source project, Gradle wrapper, and tests. It does not include Scholar's source code or compiled JAR.

A Java 21 installation is required. Build from the project directory with:

Windows:

```bat
.\gradlew.bat build
```

Linux/macOS:

```sh
bash ./gradlew build
```

Gradle downloads the required build dependencies. Output JARs are written to `build/libs/`. Development-only smoke-test code is located in `src/smoke/` and is not included in a default build.

## License and attribution

Scholar Illustrations is licensed under the GNU General Public License, version 3 only (`GPL-3.0-only`). See [LICENSE](LICENSE) for the complete terms.

Scholar is developed by **mortuusars**. This is an independent addon and is not an official Scholar project or endorsed by its developer. The addon requires Scholar, which must be obtained separately.
