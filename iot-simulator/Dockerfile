# syntax=docker/dockerfile:1
# Capteurs IoT virtuels (Python) — image construite depuis la racine du dépôt (D40)
#   docker build -f iot-simulator/Dockerfile -t smart-healthcare/iot-simulator .
# Le scénario se choisit par variable : SCENARIO=scenarios/demo-alertes.json (sinon mode simple : PATIENT, CRITIQUE_A…)
FROM python:3.12.16-slim-bookworm
RUN useradd --system --uid 10001 --no-create-home appli
WORKDIR /app
COPY iot-simulator/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY contracts/grpc contracts/grpc
COPY iot-simulator/generer.py ./
COPY iot-simulator/capteur capteur
COPY iot-simulator/scenarios scenarios
ENV PROTO_DIR=/app/contracts/grpc     TELEMETRIE_ADRESSE=grpc-telemetrie:50051     PYTHONUNBUFFERED=1     PYTHONIOENCODING=utf-8
RUN python generer.py
USER appli
CMD ["python", "-m", "capteur.simulateur"]
