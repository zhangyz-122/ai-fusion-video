"""
QC check implementations — real video analysis using ffprobe/opencv.
Each check returns a QcCheckResult.
"""
import subprocess, json, os
from typing import Optional

def _ffprobe(video_path: str) -> dict:
    """Extract video metadata via ffprobe."""
    cmd = [
        "ffprobe", "-v", "quiet", "-print_format", "json",
        "-show_format", "-show_streams", video_path
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe failed: {result.stderr[:200]}")
    return json.loads(result.stdout)

def _extract_frames(video_path: str, timestamps: list[float], out_dir: str) -> list[str]:
    """Extract frames at given timestamps."""
    paths = []
    for i, ts in enumerate(timestamps):
        out = os.path.join(out_dir, f"frame_{i}.png")
        cmd = ["ffmpeg", "-ss", str(ts), "-i", video_path, "-frames:v", "1", "-y", out]
        subprocess.run(cmd, capture_output=True, timeout=15)
        if os.path.exists(out): paths.append(out)
    return paths

def check_technical(video_url: str) -> dict:
    if not video_url or not os.path.exists(video_url):
        return {"criterion": "TECHNICAL_VALIDITY", "verdict": "FAIL", "failure_code": "FILE_NOT_FOUND"}
    try:
        meta = _ffprobe(video_url)
        streams = [s for s in meta.get("streams", []) if s.get("codec_type") == "video"]
        if not streams: return {"criterion": "TECHNICAL_VALIDITY", "verdict": "FAIL", "failure_code": "NO_VIDEO_STREAM"}
        return {"criterion": "TECHNICAL_VALIDITY", "verdict": "PASS"}
    except Exception as e:
        return {"criterion": "TECHNICAL_VALIDITY", "verdict": "FAIL", "failure_code": str(e)[:80]}

def check_duration(video_url: str, expected: float, tolerance: float = 0.5) -> dict:
    try:
        meta = _ffprobe(video_url)
        actual = float(meta.get("format", {}).get("duration", 0))
        ok = abs(actual - expected) <= tolerance
        return {"criterion": "DURATION", "verdict": "PASS" if ok else "FAIL",
                "value_score": actual, "threshold_value": expected}
    except Exception as e:
        return {"criterion": "DURATION", "verdict": "FAIL", "failure_code": str(e)[:80]}

def check_frame_extraction(video_url: str, timestamps: list[float], out_dir: str) -> dict:
    frames = _extract_frames(video_url, timestamps, out_dir)
    ok = len(frames) >= len(timestamps) * 0.8
    return {"criterion": "FRAME_EXTRACTION", "verdict": "PASS" if ok else "FAIL",
            "value_score": len(frames), "evidence_url": out_dir if frames else None}
