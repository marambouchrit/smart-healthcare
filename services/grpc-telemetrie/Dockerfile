# syntax=docker/dockerfile:1
# Service gRPC — Télémétrie & alertes (Python) — image construite depuis la racine du dépôt (D40)
#   docker build -f services/grpc-telemetrie/Dockerfile -t smart-healthcare/grpc-telemetrie .
FROM python:3.12.16-slim-bookworm
RUN useradd --system --uid 10001 --no-create-home appli
WORKDIR /app
COPY services/grpc-telemetrie/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY contracts/grpc contracts/grpc
COPY contracts/seed contracts/seed
COPY services/grpc-telemetrie/generer.py services/grpc-telemetrie/seuils.json ./
COPY services/grpc-telemetrie/telemetrie telemetrie
ENV PROTO_DIR=/app/contracts/grpc     SEED_DIR=/app/contracts/seed     PYTHONUNBUFFERED=1     PYTHONIOENCODING=utf-8
# code gRPC généré depuis le contrat au moment du build
RUN python generer.py
USER appli
EXPOSE 50051
CMD ["python", "-m", "telemetrie.serveur"]
