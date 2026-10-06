"""Copy the reference schemas, rules, worlds and demos into Kotlin source; ReferenceParityTest fails when they drift."""
import os
from pathlib import Path

DESIGN = Path(__file__).resolve().parent.parent / "reference"
MODULE = Path(__file__).resolve().parent.parent / "src"


def kotlin_raw_string(text: str) -> str:
    if '"""' in text:
        raise ValueError("a reference document contains a triple quote")
    return '"""' + text.replace("$", "${'$'}") + '"""'


def write_constants(path: Path, package: str, entries: list[tuple[str, str]], visibility: str) -> None:
    lines = [f"package {package}", ""]
    for name, relative in entries:
        text = (DESIGN / relative).read_text(encoding="utf-8")
        lines.append(f"{visibility}const val {name}: String = {kotlin_raw_string(text)}")
        lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    write_constants(MODULE / "commonMain/kotlin/ai/factoredui/worldengine/schema/ReferenceDocuments.kt",
                    "ai.factoredui.worldengine.schema",
                    [("WORLD_SCHEMA_JSON", "world.schema.json"),
                     ("EVENTS_SCHEMA_JSON", "events.schema.json"),
                     ("VALIDATION_RULES_JSON", "rules.json")],
                    "internal ")
    write_constants(MODULE / "commonTest/kotlin/ai/factoredui/worldengine/ReferenceFixtures.kt",
                    "ai.factoredui.worldengine",
                    [("PARCEL_WORLD_JSON", "worlds/parcel-five-acre.world.json"),
                     ("LOCALITY_WORLD_JSON", "worlds/locality-stub.world.json"),
                     ("DUNGEON_WORLD_JSON", "worlds/dungeon-tiny.world.json"),
                     ("LIDAR_WORLD_JSON", "worlds/parcel-lidar-sample.world.json"),
                     ("PARCEL_DEMO_JSON", "demos/parcel-five-acre.demo.json"),
                     ("DUNGEON_DEMO_JSON", "demos/dungeon-tiny.demo.json"),
                     ("MUTATIONS_JSON", "mutations.json")],
                    "")
    print(f"embedded reference documents from {DESIGN}")


if __name__ == "__main__":
    main()
