# Guia de Integracion de Webhooks y Seguridad Criptografica — DeployOps v2.0

Esta guia describe el protocolo de comunicacion, las cabeceras de seguridad y los ejemplos oficiales de validacion criptografica en cuatro lenguajes (Java 21, Node.js, Python y Go) para recibir y autenticar alertas despachadas por DeployOps.

---

## 1. Protocolo de Entrega y Cabeceras HTTP

Cada evento emitido por DeployOps se envia como una peticion `POST` con codificacion UTF-8 y cuerpo en formato JSON. Se incluyen las siguientes cabeceras HTTP de seguridad:

| Cabecera | Tipo de Dato | Descripcion |
| :--- | :--- | :--- |
| `Content-Type` | String | `application/json; charset=utf-8` |
| `User-Agent` | String | `DeployOps-Webhook-Engine/2.0` |
| `X-DeployOps-Event` | String | Tipo de evento (`INCIDENT_OPENED`, `INCIDENT_RESOLVED`, `WEBHOOK_TEST_PING`, `CHAOS_SIMULATION`). |
| `X-DeployOps-Delivery-Id` | String | Identificador unico de la entrega individual (ej. `dlv_7c9e6679-7425-40de-944b`) util para deduplicacion e idempotencia. |
| `X-DeployOps-Timestamp` | Entero (Unix Epoch) | Marca de tiempo exacta en segundos UTC en la que se calculo la firma. |
| `X-DeployOps-Signature-256` | String | Firma criptografica HMAC-SHA256 con prefijo: `sha256=<digest_hex_en_minusculas>`. |

---

## 2. Algoritmo de Firma Criptografica y Proteccion Anti-Replay

La firma previene ataques de suplantacion (*Man-in-the-Middle*) y ataques de repeticion (*Replay Attacks*). El remitente construye una cadena canonica concatenando el timestamp en segundos, un punto delimitador `.` y el cuerpo JSON crudo:

$$\text{CanonicalString} = \text{Timestamp} + "." + \text{RawBody}$$

A continuacion, se calcula el codigo de autenticacion de mensaje mediante **HMAC-SHA256** utilizando el secreto compartido `whsec_...` como clave criptografica:

$$\text{Signature} = \text{"sha256="} + \text{HexEncode}(\text{HMAC-SHA256}(\text{Secret}, \text{CanonicalString}))$$

### Reglas de Validacion para Receptores
1. **Ventana de Tolerancia Anti-Replay:** El receptor debe rechazar peticiones donde $|\text{CurrentEpochSeconds} - \text{X-DeployOps-Timestamp}| > 300$ (5 minutos).
2. **Comparacion en Tiempo Constante:** Se debe comparar el digesto recibido frente al digesto calculado usando funciones inmunes a ataques de temporizacion (*Timing Attacks*), tales como `crypto.timingSafeEqual` en Node.js o `MessageDigest.isEqual` en Java.

---

## 3. Estructura del Payload JSON Canonico

```json
{
  "eventId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "eventType": "INCIDENT_OPENED",
  "occurredAt": "2026-11-07T15:20:00.123456",
  "serviceId": 42,
  "serviceName": "Payment Gateway API",
  "url": "https://pay.empresa.com/health",
  "status": "DOWN",
  "httpStatus": 503,
  "incidentId": 105
}
```

---

## 4. Ejemplos de Implementacion de Referencia

### 4.1. Node.js / Express (TypeScript)

```typescript
import express, { Request, Response } from 'express';
import crypto from 'node:crypto';

const app = express();
// Importante: Conservar el buffer o string crudo sin parsear para calcular el HMAC
app.use(express.raw({ type: 'application/json' }));

function verifyDeployOpsWebhook(req: Request, secret: string): boolean {
  const timestampHeader = req.header('x-deployops-timestamp');
  const signatureHeader = req.header('x-deployops-signature-256');

  if (!timestampHeader || !signatureHeader) return false;

  const timestamp = Number(timestampHeader);
  const now = Math.floor(Date.now() / 1000);

  // 1. Ventana Anti-Replay de 300 segundos
  if (Math.abs(now - timestamp) > 300) {
    console.warn('Firma rechazada: timestamp fuera de ventana.');
    return false;
  }

  // 2. Calcular HMAC sobre "timestamp.rawBody"
  const rawBody = req.body.toString('utf8');
  const expectedSignature = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(`${timestamp}.${rawBody}`, 'utf8')
    .digest('hex');

  // 3. Comparacion en tiempo constante
  const expectedBuf = Buffer.from(expectedSignature, 'utf8');
  const receivedBuf = Buffer.from(signatureHeader, 'utf8');

  return expectedBuf.length === receivedBuf.length && crypto.timingSafeEqual(expectedBuf, receivedBuf);
}

app.post('/webhook-handler', (req: Request, res: Response) => {
  const secret = process.env.DEPLOYOPS_WEBHOOK_SECRET || 'whsec_...';

  if (!verifyDeployOpsWebhook(req, secret)) {
    return res.status(401).send('Firma criptografica invalida o expirada.');
  }

  const payload = JSON.parse(req.body.toString('utf8'));
  console.log(`Evento recibido: ${payload.eventType} para servicio ${payload.serviceName}`);

  // Responder 200 OK inmediatamente
  res.status(200).send({ received: true });
});
```

### 4.2. Java 21 / Spring Boot

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@RestController
public class DeployOpsWebhookController {

    private final String webhookSecret = System.getenv().getOrDefault("DEPLOYOPS_WEBHOOK_SECRET", "whsec_...");

    @PostMapping("/webhook-handler")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-DeployOps-Timestamp") long timestamp,
            @RequestHeader("X-DeployOps-Signature-256") String receivedSignature,
            @RequestBody String rawPayload
    ) {
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > 300) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Timestamp fuera de ventana");
        }

        try {
            String canonicalData = timestamp + "." + rawPayload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = "sha256=" + HexFormat.of().formatHex(mac.doFinal(canonicalData.getBytes(StandardCharsets.UTF_8)));

            if (!MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), receivedSignature.getBytes(StandardCharsets.UTF_8))) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Firma no coincide");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok("Evento procesado");
    }
}
```

### 4.3. Python / FastAPI

```python
import hmac
import hashlib
import time
from fastapi import FastAPI, Header, HTTPException, Request

app = FastAPI()
WEBHOOK_SECRET = "whsec_tu_clave_secreta"

@app.post("/webhook-handler")
async def receive_webhook(
    request: Request,
    x_deployops_timestamp: int = Header(...),
    x_deployops_signature_256: str = Header(...)
):
    now = int(time.time())
    if abs(now - x_deployops_timestamp) > 300:
        raise HTTPException(status_code=401, detail="Desvio temporal excesivo (Anti-Replay)")

    raw_body = await request.body()
    canonical_data = f"{x_deployops_timestamp}.".encode("utf-8") + raw_body

    expected_sig = "sha256=" + hmac.new(
        WEBHOOK_SECRET.encode("utf-8"),
        canonical_data,
        hashlib.sha256
    ).hexdigest()

    if not hmac.compare_digest(expected_sig, x_deployops_signature_256):
        raise HTTPException(status_code=401, detail="Firma criptografica invalida")

    return {"status": "accepted"}
```

### 4.4. Go

```go
package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"math"
	"net/http"
	"strconv"
	"time"
)

const webhookSecret = "whsec_tu_clave_secreta"

func webhookHandler(w http.ResponseWriter, r *http.Request) {
	tsStr := r.Header.Get("X-DeployOps-Timestamp")
	receivedSig := r.Header.Get("X-DeployOps-Signature-256")

	ts, err := strconv.ParseInt(tsStr, 10, 64)
	if err != nil || math.Abs(float64(time.Now().Unix()-ts)) > 300 {
		http.Error(w, "Timestamp invalido o expirado", http.StatusUnauthorized)
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Error al leer payload", http.StatusBadRequest)
		return
	}

	canonical := fmt.Sprintf("%d.%s", ts, string(body))
	mac := hmac.New(sha256.New, []byte(webhookSecret))
	mac.Write([]byte(canonical))
	expectedSig := "sha256=" + hex.EncodeToString(mac.Sum(nil))

	if !hmac.Equal([]byte(expectedSig), []byte(receivedSig)) {
		http.Error(w, "Firma invalida", http.StatusUnauthorized)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{"received":true}`))
}
```
