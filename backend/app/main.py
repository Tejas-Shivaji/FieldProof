from __future__ import annotations

import time
import uuid
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from . import models, schemas
from .database import UPLOAD_ROOT, Base, engine, get_db
from .hashing import sha256_file

app = FastAPI(
    title="FieldProof API",
    version="1.0.0",
    description="Conflict-preserving sync API for the FieldProof Android client.",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def on_startup() -> None:
    Base.metadata.create_all(bind=engine)
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _evidence_dto(row: models.Evidence | None) -> schemas.EvidenceDto | None:
    if row is None:
        return None
    return schemas.EvidenceDto(
        id=row.id,
        sha256_hex=row.sha256_hex,
        byte_length=row.byte_length,
        mime_type=row.mime_type,
    )


def to_out(row: models.Observation) -> schemas.ObservationOut:
    return schemas.ObservationOut(
        id=row.id,
        title=row.title,
        notes=row.notes,
        captured_at_epoch_ms=row.captured_at_epoch_ms,
        latitude=row.latitude,
        longitude=row.longitude,
        location_accuracy_meters=row.location_accuracy_meters,
        location_provider=row.location_provider,
        revision=row.revision,
        updated_at_epoch_ms=row.updated_at_epoch_ms,
        deleted=row.deleted,
        evidence=_evidence_dto(row.evidence),
    )


def same_payload(row: models.Observation, body: schemas.ObservationPush) -> bool:
    evidence_match = True
    if body.evidence is not None:
        evidence_match = (
            row.evidence is not None
            and row.evidence.sha256_hex.lower() == body.evidence.sha256_hex.lower()
        )
    return (
        row.title == body.title
        and row.notes == body.notes
        and row.captured_at_epoch_ms == body.captured_at_epoch_ms
        and row.deleted == body.deleted
        and evidence_match
    )


def add_version(db: Session, row: models.Observation, source: str, sha: str | None) -> None:
    db.add(
        models.ObservationVersion(
            id=str(uuid.uuid4()),
            observation_id=row.id,
            source=source,
            revision=row.revision,
            title=row.title,
            notes=row.notes,
            captured_at_epoch_ms=row.captured_at_epoch_ms,
            latitude=row.latitude,
            longitude=row.longitude,
            evidence_sha256_hex=sha,
            recorded_at_epoch_ms=int(time.time() * 1000),
        )
    )


def apply_body(row: models.Observation, body: schemas.ObservationPush) -> None:
    now = int(time.time() * 1000)
    row.title = body.title
    row.notes = body.notes
    row.captured_at_epoch_ms = body.captured_at_epoch_ms
    row.latitude = body.latitude
    row.longitude = body.longitude
    row.location_accuracy_meters = body.location_accuracy_meters
    row.location_provider = body.location_provider
    row.deleted = body.deleted
    row.updated_at_epoch_ms = now
    if body.evidence is not None:
        if row.evidence is None:
            row.evidence = models.Evidence(
                id=body.evidence.id,
                observation_id=row.id,
                sha256_hex=body.evidence.sha256_hex.lower(),
                byte_length=body.evidence.byte_length,
                mime_type=body.evidence.mime_type,
            )
        else:
            row.evidence.sha256_hex = body.evidence.sha256_hex.lower()
            row.evidence.byte_length = body.evidence.byte_length
            row.evidence.mime_type = body.evidence.mime_type


@app.post("/v1/observations", response_model=schemas.ObservationOut)
def push_observation(body: schemas.ObservationPush, db: Session = Depends(get_db)):
    row = db.get(models.Observation, body.id)
    now = int(time.time() * 1000)
    if row is None:
        row = models.Observation(
            id=body.id,
            title=body.title,
            notes=body.notes,
            captured_at_epoch_ms=body.captured_at_epoch_ms,
            latitude=body.latitude,
            longitude=body.longitude,
            location_accuracy_meters=body.location_accuracy_meters,
            location_provider=body.location_provider,
            revision=1,
            updated_at_epoch_ms=now,
            deleted=body.deleted,
        )
        db.add(row)
        apply_body(row, body)
        row.revision = 1
        add_version(db, row, "CLIENT", body.evidence.sha256_hex if body.evidence else None)
        db.commit()
        db.refresh(row)
        return to_out(row)

    if same_payload(row, body):
        return to_out(row)

    base = body.last_synced_revision or 0
    if base == row.revision:
        apply_body(row, body)
        row.revision = row.revision + 1
        add_version(db, row, "CLIENT", body.evidence.sha256_hex if body.evidence else None)
        db.commit()
        db.refresh(row)
        return to_out(row)

    add_version(db, row, "CONFLICT_SERVER", row.evidence.sha256_hex if row.evidence else None)
    db.commit()
    return JSONResponse(
        status_code=409,
        content=schemas.ConflictOut(server=to_out(row)).model_dump(),
    )


@app.get("/v1/observations", response_model=schemas.PullOut)
def pull_observations(updated_since_epoch_ms: int = 0, db: Session = Depends(get_db)):
    rows = db.scalars(
        select(models.Observation).where(models.Observation.updated_at_epoch_ms >= updated_since_epoch_ms)
    ).all()
    return schemas.PullOut(
        observations=[to_out(row) for row in rows],
        server_time_epoch_ms=int(time.time() * 1000),
    )


@app.get("/v1/observations/{observation_id}", response_model=schemas.ObservationOut)
def get_observation(observation_id: str, db: Session = Depends(get_db)):
    row = db.get(models.Observation, observation_id)
    if row is None:
        raise HTTPException(status_code=404, detail="not found")
    return to_out(row)


@app.get("/v1/observations/{observation_id}/versions")
def list_versions(observation_id: str, db: Session = Depends(get_db)):
    row = db.get(models.Observation, observation_id)
    if row is None:
        raise HTTPException(status_code=404, detail="not found")
    return [
        {
            "id": version.id,
            "source": version.source,
            "revision": version.revision,
            "title": version.title,
            "notes": version.notes,
            "evidence_sha256_hex": version.evidence_sha256_hex,
            "recorded_at_epoch_ms": version.recorded_at_epoch_ms,
        }
        for version in row.versions
    ]


@app.post("/v1/observations/{observation_id}/evidence", response_model=schemas.EvidenceDto)
async def upload_evidence(
    observation_id: str,
    sha256_hex: str = Form(...),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    row = db.get(models.Observation, observation_id)
    if row is None:
        raise HTTPException(status_code=404, detail="observation not found")
    folder = UPLOAD_ROOT / observation_id
    folder.mkdir(parents=True, exist_ok=True)
    suffix = Path(file.filename or "evidence.bin").suffix or ".bin"
    dest = folder / f"evidence{suffix}"
    payload = await file.read()
    dest.write_bytes(payload)
    digest, length = sha256_file(dest)
    if digest.lower() != sha256_hex.strip().lower():
        dest.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="sha256 mismatch")
    if row.evidence is None:
        row.evidence = models.Evidence(
            id=str(uuid.uuid4()),
            observation_id=row.id,
            sha256_hex=digest,
            byte_length=length,
            mime_type=file.content_type or "application/octet-stream",
            relative_path=str(dest),
        )
    else:
        row.evidence.sha256_hex = digest
        row.evidence.byte_length = length
        row.evidence.mime_type = file.content_type or row.evidence.mime_type
        row.evidence.relative_path = str(dest)
    row.updated_at_epoch_ms = int(time.time() * 1000)
    db.commit()
    db.refresh(row)
    return _evidence_dto(row.evidence)
