#!/usr/bin/env python3
"""Regenerate the committed GameTest arena using Python's standard library."""

import gzip
import struct
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DESTINATION = PROJECT_ROOT / "src/gameTest/resources/data/sheeper/structures/test_arena.nbt"
WIDTH = 24
HEIGHT = 9
DEPTH = 24
DATA_VERSION = 3120  # Minecraft 1.19.2.

TAG_INT = 3
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_END = b"\x00"


def nbt_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def nbt_int(value: int) -> bytes:
    return struct.pack(">i", value)


def named_tag(kind: int, name: str, payload: bytes) -> bytes:
    return bytes([kind]) + nbt_string(name) + payload


def list_tag(name: str, kind: int, values: list[bytes]) -> bytes:
    # NBT lists store one shared element type; entries have neither names nor tag IDs.
    return named_tag(TAG_LIST, name, bytes([kind]) + nbt_int(len(values)) + b"".join(values))


def create_arena() -> bytes:
    floor = [
        list_tag("pos", TAG_INT, [nbt_int(x), nbt_int(0), nbt_int(z)])
        + named_tag(TAG_INT, "state", nbt_int(0))
        + TAG_END
        for x in range(WIDTH)
        for z in range(DEPTH)
    ]
    payload = (
        named_tag(TAG_INT, "DataVersion", nbt_int(DATA_VERSION))
        + list_tag("size", TAG_INT, [nbt_int(WIDTH), nbt_int(HEIGHT), nbt_int(DEPTH)])
        + list_tag(
            "palette",
            TAG_COMPOUND,
            [named_tag(TAG_STRING, "Name", nbt_string("minecraft:bedrock")) + TAG_END],
        )
        + list_tag("blocks", TAG_COMPOUND, floor)
        + list_tag("entities", TAG_COMPOUND, [])
        + TAG_END
    )
    # The root is an unnamed compound; a fixed gzip timestamp keeps regeneration stable.
    return gzip.compress(bytes([TAG_COMPOUND]) + nbt_string("") + payload, mtime=0)


def main() -> None:
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    DESTINATION.write_bytes(create_arena())
    print(f"Generated {DESTINATION.relative_to(PROJECT_ROOT)} ({WIDTH} x {HEIGHT} x {DEPTH}, bedrock floor).")


if __name__ == "__main__":
    main()
