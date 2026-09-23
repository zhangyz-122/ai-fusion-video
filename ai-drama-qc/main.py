import os
import time
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

from checks import (
    check_duration,
    check_frame_extraction,
    check_opencv_brightness,
    check_opencv_motion,
    check_technical,
    resolve_media_input,
)

app = FastAPI(title="AI Drama QC Sidecar", version="1.0.0")

FRAME_OUTPUT_DIR = os.environ.get("QC_FRAME_OUTPUT_DIR", "/tmp/qc-frames")

# 尚无检测实现的检查项一律返回 REVIEW_REQUIRED，不允许伪造 PASS
DEFAULT_CHECKS = [
    "TECHNICAL_VALIDITY",
    "DURATION",
    "FRAME_EXTRACTION",
    "CHARACTER_COUNT",
    "BASIC_IDENTITY",
    "PROMPT_MATCH",
    "TEMPORAL",
    "AESTHETIC",
]


class EvaluateRequest(BaseModel):
    video_url: str
    first_frame_url: Optional[str] = None
    last_frame_url: Optional[str] = None
    prompt: str = ""
    expected_character_count: int = 1
    expected_duration_seconds: float = Field(..., gt=0)
    checks: list[str] = Field(default_factory=lambda: list(DEFAULT_CHECKS))


class QcCheckResult(BaseModel):
    criterion: str
    verdict: str
    value_score: Optional[float] = None
    threshold_value: Optional[float] = None
    failure_code: Optional[str] = None
    evidence_url: Optional[str] = None


class EvaluateResponse(BaseModel):
    overall_verdict: str
    checks: list[QcCheckResult]
    elapsed_ms: int


def _sample_timestamps(duration_seconds: float) -> list[float]:
    return [round(duration_seconds * ratio, 3) for ratio in (0.0, 0.25, 0.5, 0.75, 0.99)]


def _run_check(criterion: str, video_path: str, req: EvaluateRequest) -> dict:
    if criterion == "TECHNICAL_VALIDITY":
        return check_technical(video_path)
    if criterion == "DURATION":
        return check_duration(video_path, req.expected_duration_seconds)
    if criterion == "FRAME_EXTRACTION":
        return check_frame_extraction(video_path, _sample_timestamps(req.expected_duration_seconds), FRAME_OUTPUT_DIR)
    if criterion == "MOTION_ANALYSIS":
        return check_opencv_motion(video_path, FRAME_OUTPUT_DIR)
    if criterion == "BRIGHTNESS":
        return check_opencv_brightness(video_path)
    return {"criterion": criterion, "verdict": "REVIEW_REQUIRED", "failure_code": "CHECK_NOT_IMPLEMENTED"}


def _overall_verdict(verdicts: set[str]) -> str:
    if "FAIL" in verdicts:
        return "FAIL"
    if verdicts == {"PASS"}:
        return "PASS"
    return "REVIEW_REQUIRED"


@app.get("/health")
def health():
    return {"status": "ok", "service": "ai-drama-qc-sidecar", "version": "1.0.0"}


@app.post("/evaluate", response_model=EvaluateResponse)
def evaluate(req: EvaluateRequest):
    t0 = time.monotonic()
    video_path = resolve_media_input(req.video_url)
    results = [QcCheckResult(**_run_check(criterion, video_path, req)) for criterion in req.checks]
    overall = _overall_verdict({result.verdict for result in results})
    return EvaluateResponse(
        overall_verdict=overall,
        checks=results,
        elapsed_ms=int((time.monotonic() - t0) * 1000),
    )
