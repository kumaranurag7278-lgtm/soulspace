# SoulSpace

Pure Java (Spring Boot 3.3, Java 17+) backend serving a static frontend from `src/main/resources/static`.

Run: `./mvnw spring-boot:run` (Mac/Linux) or `.\mvnw.cmd spring-boot:run` (Windows), then open http://localhost:8090

- Local DB: file-based H2 at `./data/soulspace` (persists across restarts).
- AI Companion: set `GEMINI_API_KEY=your_key` to enable Aria's conversational features via Google Gemini.
- Production: set `SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db` plus `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`; also `SESSION_COOKIE_SECURE=true` behind HTTPS.
- `REVIEWS_REQUIRE_BOOKING=false` lets any signed-in user review any clinician.

Demo notes: clinicians, ratings and payments are simulated sample data. The private room's clinician side is not built.
