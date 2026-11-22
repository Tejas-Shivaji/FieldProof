from __future__ import annotations

from pydantic import BaseModel, Field


class EvidenceDto(BaseModel):
    id: str
    sha256_hex: str
    byte_length: int
    mime_type: str


class ObservationPush(BaseModel):
    id: str
    title: str
    notes: str = ""
    captured_at_epoch_ms: int
    latitude: float | None = None
    longitude: float | None = None
    location_accuracy_meters: float | None = None
    location_provider: str | None = None
    local_revision: int = Field(ge=1)
    last_synced_revision: int | None = None
    deleted: bool = False
    evidence: EvidenceDto | None = None


class ObservationOut(BaseModel):
    id: str
    title: str
    notes: str
    captured_at_epoch_ms: int
    latitude: float | None
    longitude: float | None
    location_accuracy_meters: float | None
    location_provider: str | None
    revision: int
    updated_at_epoch_ms: int
    deleted: bool
    evidence: EvidenceDto | None = None

    model_config = {"from_attributes": True}


class ConflictOut(BaseModel):
    code: str = "CONFLICT"
    server: ObservationOut


class PullOut(BaseModel):
    observations: list[ObservationOut]
    server_time_epoch_ms: int
