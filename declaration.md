# Tool-use declaration

**I used the following external resources and tools while completing this assessment:**
Official documentation (Spring Boot, Spring Data R2DBC, PostgreSQL, React, Vite, Vitest,
Testcontainers, Docker Compose), my own IDE and its tooling, Docker, and a generative AI assistant
as described below. No CIDRZ systems, paid cloud services or licensed software were used; everything
here runs on an ordinary laptop.

**Generative AI tools used:**
Claude (Anthropic), used through Claude Code in the terminal.

**I used these tools for:**

- Rewriting the API as the Gradle, Spring Boot 4 module in `backend/`, carrying over an earlier
  Maven implementation and restructuring it to the package layout, exception hierarchy, DTO and
  repository conventions I use in my own projects.
- Writing and running the automated tests for `backend/`, and diagnosing the failures they found.
- Writing `scripts/run.sh`, and the Docker Compose and Vite changes that let it run on a machine
  where port 8080 was already taken.
- Drafting and editing the written sections, and checking factual claims in them against the running
  system rather than leaving them as assertions.
- Measuring the API's query latency and the front-end render figures quoted in the README.

**The following parts of my submission were substantially generated with AI assistance:**

- `backend/` — the Spring Boot module, including its tests, migration and configuration.
- `scripts/run.sh`.
- `C-review.md`, `D-resilience.md`, `F-practice.md`, `G-briefing.md`, and this declaration.
- `E-presentation.pdf`.
- Substantial edits to `README.md` and `A-architecture.md`.

**I confirm that I understand the contents of my submission and can explain, present and modify the
solution during the technical interview.**

Name: Yengwe Tepula  Position applied for: Senior Software Engineer Date: 7th October 2026
