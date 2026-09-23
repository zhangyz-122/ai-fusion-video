FROM python:3.11-slim
WORKDIR /app
# ffprobe/ffmpeg 是 DURATION、FRAME_EXTRACTION 检测项的运行时依赖
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py checks.py ./
EXPOSE 9900
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "9900"]
