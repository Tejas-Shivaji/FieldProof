from __future__ import annotations

import hashlib
import io
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402

engine = create_engine(
    "sqlite://",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSession = sessionmaker(bind=engine, autoflush=False, autocommit=False)
Base.metadata.create_all(bind=engine)


def override_db():
    db = TestingSession()
    try:
        yield db
    finally:
        db.close()


app.dependency_overrides[get_db] = override_db
client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_db():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield


def sample_body(**overrides):
    body = {
        "id": "11111111-1111-1111-1111-111111111111",
        "title": "Bridge",
        "notes": "bridge damaged",
        "captured_at_epoch_ms": 1_700_000_000_000,
        "latitude": 12.3,
        "longitude": 45.6,
        "location_accuracy_meters": 8.0,
        "location_provider": "gps",
        "local_revision": 1,
        "last_synced_revision": None,
        "deleted": False,
        "evidence": {
            "id": "22222222-2222-2222-2222-222222222222",
            "sha256_hex": "a" * 64,
            "byte_length": 12,
            "mime_type": "image/jpeg",
        },
    }
    body.update(overrides)
    return body


def test_health():
    assert client.get("/health").json()["status"] == "ok"


def test_create_is_idempotent():
    first = client.post("/v1/observations", json=sample_body())
    second = client.post("/v1/observations", json=sample_body())
    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["revision"] == 1
    assert second.json()["revision"] == 1


def test_conflict_preserves_server_row():
    created = client.post("/v1/observations", json=sample_body())
    assert created.status_code == 200
    conflict = client.post(
        "/v1/observations",
        json=sample_body(
            notes="bridge OK",
            local_revision=2,
            last_synced_revision=0,
        ),
    )
    assert conflict.status_code == 409
    payload = conflict.json()
    assert payload["code"] == "CONFLICT"
    assert payload["server"]["notes"] == "bridge damaged"
    stored = client.get("/v1/observations/11111111-1111-1111-1111-111111111111").json()
    assert stored["notes"] == "bridge damaged"


def test_fast_forward_when_base_matches():
    client.post("/v1/observations", json=sample_body())
    updated = client.post(
        "/v1/observations",
        json=sample_body(
            notes="bridge damaged, photo taken",
            local_revision=2,
            last_synced_revision=1,
        ),
    )
    assert updated.status_code == 200
    assert updated.json()["revision"] == 2
    assert updated.json()["notes"] == "bridge damaged, photo taken"


def test_pull_returns_created_row():
    client.post("/v1/observations", json=sample_body())
    pulled = client.get("/v1/observations", params={"updated_since_epoch_ms": 0})
    assert pulled.status_code == 200
    assert len(pulled.json()["observations"]) == 1


def test_evidence_hash_mismatch_rejected(tmp_path, monkeypatch):
    client.post("/v1/observations", json=sample_body())
    from app import main as main_mod

    monkeypatch.setattr(main_mod, "UPLOAD_ROOT", tmp_path)
    data = b"hello-evidence"
    wrong = hashlib.sha256(b"nope").hexdigest()
    response = client.post(
        "/v1/observations/11111111-1111-1111-1111-111111111111/evidence",
        data={"sha256_hex": wrong},
        files={"file": ("shot.jpg", io.BytesIO(data), "image/jpeg")},
    )
    assert response.status_code == 400
