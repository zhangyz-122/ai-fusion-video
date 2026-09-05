"""
QC check implementations — real video analysis using ffprobe/opencv.
Each check returns a dict suitable for QcCheckResult.
"""
import subprocess, json, os
from typing import Optional

def _ffprobe(video_path: str) -> dict:
    cmd = ["ffprobe", "-v", "quiet", "-print_format", "json",
           "-show_format", "-show_streams", video_path]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(f"ffprobe failed: {result.stderr[:200]}")
    return json.loads(result.stdout)

def _extract_frames(video_path: str, timestamps: list[float], out_dir: str) -> list[str]:
    paths = []
    os.makedirs(out_dir, exist_ok=True)
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
        if not streams:
            return {"criterion": "TECHNICAL_VALIDITY", "verdict": "FAIL", "failure_code": "NO_VIDEO_STREAM"}
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

def check_opencv_motion(video_url: str, out_dir: str) -> dict:
    """OpenCV motion analysis: frame difference score."""
    try:
        import cv2
        cap = cv2.VideoCapture(video_url)
        prev = None
        diffs = []
        frame_idx = 0
        while True:
            ret, frame = cap.read()
            if not ret: break
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            gray = cv2.resize(gray, (320, 180))
            if prev is not None:
                diff = cv2.absdiff(gray, prev)
                diffs.append(float(diff.mean()))
            prev = gray
            frame_idx += 1
            if frame_idx > 300: break
        cap.release()
        avg_motion = sum(diffs) / len(diffs) if diffs else 0
        return {"criterion": "MOTION_ANALYSIS", "verdict": "PASS",
                "value_score": round(avg_motion, 2), "frame_count": frame_idx}
    except Exception as e:
        return {"criterion": "MOTION_ANALYSIS", "verdict": "FAIL", "failure_code": str(e)[:80]}

def check_opencv_brightness(video_url: str) -> dict:
    """Check if video has reasonable brightness (not too dark/bright)."""
    try:
        import cv2
        cap = cv2.VideoCapture(video_url)
        ret, frame = cap.read()
        cap.release()
        if not ret:
            return {"criterion": "BRIGHTNESS", "verdict": "FAIL", "failure_code": "CANNOT_READ"}
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        mean = float(gray.mean())
        verdict = "PASS" if 20 < mean < 235 else "REVIEW_REQUIRED"
        return {"criterion": "BRIGHTNESS", "verdict": verdict, "value_score": round(mean, 1)}
    except Exception as e:
        return {"criterion": "BRIGHTNESS", "verdict": "FAIL", "failure_code": str(e)[:80]}
