from __future__ import annotations

from sqlalchemy import BigInteger, Boolean, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


class Observation(Base):
    __tablename__ = "observations"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    title: Mapped[str] = mapped_column(String(200))
    notes: Mapped[str] = mapped_column(Text, default="")
    captured_at_epoch_ms: Mapped[int] = mapped_column(BigInteger)
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    location_accuracy_meters: Mapped[float | None] = mapped_column(Float, nullable=True)
    location_provider: Mapped[str | None] = mapped_column(String(64), nullable=True)
    revision: Mapped[int] = mapped_column(Integer, default=1)
    updated_at_epoch_ms: Mapped[int] = mapped_column(BigInteger)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False)

    evidence: Mapped["Evidence | None"] = relationship(
        back_populates="observation",
        uselist=False,
        cascade="all, delete-orphan",
    )
    versions: Mapped[list["ObservationVersion"]] = relationship(
        back_populates="observation",
        cascade="all, delete-orphan",
    )


class Evidence(Base):
    __tablename__ = "evidence"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    observation_id: Mapped[str] = mapped_column(ForeignKey("observations.id"), unique=True)
    sha256_hex: Mapped[str] = mapped_column(String(64))
    byte_length: Mapped[int] = mapped_column(BigInteger)
    mime_type: Mapped[str] = mapped_column(String(80))
    relative_path: Mapped[str | None] = mapped_column(String(400), nullable=True)

    observation: Mapped[Observation] = relationship(back_populates="evidence")


class ObservationVersion(Base):
    __tablename__ = "observation_versions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    observation_id: Mapped[str] = mapped_column(ForeignKey("observations.id"))
    source: Mapped[str] = mapped_column(String(16))
    revision: Mapped[int] = mapped_column(Integer)
    title: Mapped[str] = mapped_column(String(200))
    notes: Mapped[str] = mapped_column(Text)
    captured_at_epoch_ms: Mapped[int] = mapped_column(BigInteger)
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    evidence_sha256_hex: Mapped[str | None] = mapped_column(String(64), nullable=True)
    recorded_at_epoch_ms: Mapped[int] = mapped_column(BigInteger)

    observation: Mapped[Observation] = relationship(back_populates="versions")
