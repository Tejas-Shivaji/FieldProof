from __future__ import annotations

import hashlib
import hmac
from pathlib import Path


def sha256_file(path: Path, chunk_size: int = 64 * 1024) -> tuple[str, int]:
    digest = hashlib.sha256()
    total = 0
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(chunk_size)
            if not chunk:
                break
            digest.update(chunk)
            total += len(chunk)
    return digest.hexdigest(), total


def equal_hex(left: str, right: str) -> bool:
    a = left.strip().lower().encode("ascii")
    b = right.strip().lower().encode("ascii")
    if len(a) != len(b):
        return False
    return hmac.compare_digest(a, b)
