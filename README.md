# AI Text & Speech (OpenAI + Spring Boot)

This Spring Boot API integrates with OpenAI models to:

1. **Generate audio from text** (Text → Speech).
2. **Read audio and transcribe it to text** (Speech → Text).
3. **Detect checkout / shopping flow failures** from the transcribed text using an LLM.
4. If a failure is detected, **recommend/generate** a discount coupon and return a **barcode image (PNG)**.

---

## High-level design

### System diagram

> If you’re viewing this in GitHub, the diagram below should render automatically. If it doesn’t, ensure Mermaid rendering is enabled in your Markdown viewer.

```mermaid
flowchart LR
  Client[Client<br/>(Postman / UI)]

  Client -->|POST /api/text-to-speech<br/>JSON {question}| TTS
  Client -->|POST /api/speech-to-text<br/>multipart file + conversionType| STT

  subgraph API[Spring Boot API]
    TTS[TextSpeechController<br/>/text-to-speech]
    STT[TextSpeechController<br/>/speech-to-text]
    Svc[AIServiceImpl]
    Barcode[BarCodeService]
  end

  TTS --> Svc
  STT --> Svc

  Svc -->|TTS| OpenAI_TTS[OpenAI TTS<br/>(TTS-1)]
  Svc -->|Transcribe| OpenAI_Whisper[OpenAI Whisper<br/>(whisper-1)]
  Svc -->|Polish + classify| OpenAI_LLM[OpenAI Chat Model]

  Svc -->|If checkout/shopping failure detected| Barcode
  Barcode --> Ninjas[API Ninjas<br/>Barcode/QR generation]

  STT -->|Returns image/png barcode| Client
```

### Discount decision flow

```mermaid
sequenceDiagram
  autonumber
  actor C as Client
  participant A as TextSpeechController
  participant S as AIServiceImpl
  participant W as OpenAI Whisper
  participant L as OpenAI Chat Model
  participant B as BarCodeService
  participant N as API Ninjas

  C->>A: POST /api/speech-to-text (multipart audio)
  A->>S: convertAudioToText(file, conversionType)
  S->>W: Transcribe audio (whisper-1)
  W-->>S: transcribedText

  alt conversionType == polished
    S->>L: Polish transcribed text
    L-->>S: polishedText
  end

  S->>L: Classify: does it mention checkout/shopping/shipping failure?
  L-->>S: DiscountDecision(applyDiscount=true/false)

  alt applyDiscount == true
    S->>B: generateDiscountCoupon(code)
    B->>N: Generate barcode image (PNG)
    N-->>B: PNG bytes (base64)
    B-->>S: ImagePayload(image/png, base64)
    S-->>A: ImagePayload
    A-->>C: 200 image/png (barcode)
  else applyDiscount == false
    S-->>A: ImagePayload(text/plain, message)
    A-->>C: 200 text/plain (message)
  end
```

---

## API endpoints

### 1) Text → Speech

**Request**
- `POST /api/text-to-speech`
- `Content-Type: application/json`

Body:
```json
{ "question": "Hello! This will be converted to audio." }
```

**Response**
- `200 OK`
- `Content-Type: audio/mpeg`
- Body: MP3 bytes

---

### 2) Speech → Text (+ discount recommendation)

**Request**
- `POST /api/speech-to-text`
- `Content-Type: multipart/form-data`

Form fields:
- `file` (required): audio file
- `conversionType` (optional): `raw` (default) or `polished`

**Behavior**
- Audio is transcribed using Whisper.
- If `conversionType=polished`, the transcript is cleaned up with an LLM.
- The LLM checks whether the transcript contains references to failures in:
  - checkout flows
  - shopping flows
  - shipping flows
- If yes → generates a discount coupon barcode and returns it as a PNG image.

**Response variants**
- If discount is recommended:
  - `200 OK`
  - `Content-Type: image/png`
  - Body: PNG bytes (barcode image)
- If discount is not recommended:
  - `200 OK`
  - `Content-Type: text/plain`
  - Body: explanatory message

---

## Configuration

`src/main/resources/application.yml`

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

app:
  blocked-words:
    - umm
    - ahhh
    - youknow

ninjas:
  api:
    key: ${NINJAS_API_KEY}
```

### Required environment variables

- `OPENAI_API_KEY` – OpenAI API key
- `NINJAS_API_KEY` – API Ninjas key (barcode generation)

---

## Run locally

```bash
./mvnw spring-boot:run
```

Server runs on `http://localhost:8080`.

---

## Notes / Implementation details

- The discount decision is made in `AIServiceImpl#checkIfDiscount(...)` using a structured output (`BeanOutputConverter<DiscountDecision>`).
- The actual coupon code currently uses a placeholder (`DISCOUNT2024`). You can swap this to generate unique codes.
- The controller currently always returns `image/png` on success and decodes a base64 string from `ImagePayload`. If you want the API to return JSON containing both transcript + barcode, we can adjust the response contract.

---

## Troubleshooting

- **Getting 400 Invalid conversionType**: Use only `raw` or `polished`.
- **No PNG returned**: Make sure the transcript actually includes checkout/shopping/shipping failure keywords and that Ninja API key is configured.
- **OpenAI errors**: verify `OPENAI_API_KEY` is set and your account has access to Whisper/TTS.
