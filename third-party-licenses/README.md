# Third-party software inventory

Third-party software linked to or connected with **TactiCP**, with the licence text of each in
this directory.

Generated 2026-11-04 from `pom.xml` via `mvn dependency:tree` and
`license-maven-plugin:add-third-party`. Raw tool output is kept in
`GENERATED-dependency-tree.txt` and `GENERATED-third-party-report.txt`.

Scope: **54 Java artifacts** (5 declared in `pom.xml`, the rest pulled in transitively; the
compile-scope ones ship inside `TactiCP-1.0-web.jar`, so they carry attribution obligations too),
plus the Python libraries used by the experiment scripts, plus the non-library third parties
(language-model services, the dataset, and the predictions of an action-spotting model).

---

## 1. Java dependencies

### Apache License 2.0 — `Apache-2.0.txt`

| Artifact | Version | Direct? | Role |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-web` | 3.2.5 | yes | web application framework |
| `org.springframework.boot:spring-boot` | 3.2.5 | transitive | " |
| `org.springframework.boot:spring-boot-autoconfigure` | 3.2.5 | transitive | " |
| `org.springframework.boot:spring-boot-starter` | 3.2.5 | transitive | " |
| `org.springframework.boot:spring-boot-starter-json` | 3.2.5 | transitive | " |
| `org.springframework.boot:spring-boot-starter-logging` | 3.2.5 | transitive | " |
| `org.springframework.boot:spring-boot-starter-tomcat` | 3.2.5 | transitive | " |
| `org.springframework:spring-core` | 6.1.6 | transitive | " |
| `org.springframework:spring-beans` | 6.1.6 | transitive | " |
| `org.springframework:spring-context` | 6.1.6 | transitive | " |
| `org.springframework:spring-web` | 6.1.6 | transitive | " |
| `org.springframework:spring-webmvc` | 6.1.6 | transitive | " |
| `org.springframework:spring-aop` | 6.1.6 | transitive | " |
| `org.springframework:spring-expression` | 6.1.6 | transitive | " |
| `org.springframework:spring-jcl` | 6.1.6 | transitive | " |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.20 | transitive | embedded HTTP server |
| `org.apache.tomcat.embed:tomcat-embed-el` | 10.1.20 | transitive | " |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 10.1.20 | transitive | " |
| `org.apache.groovy:groovy` | 4.0.21 | yes | evaluates the DSL query string (`QueryEvaluator`) |
| `tools.jackson.core:jackson-databind` | 3.0.0 | yes | JSON parsing of tracking data |
| `tools.jackson.core:jackson-core` | 3.0.0 | transitive | " |
| `com.fasterxml.jackson.core:jackson-databind` | 2.15.4 | transitive | " (Spring's copy) |
| `com.fasterxml.jackson.core:jackson-core` | 2.15.4 | transitive | " |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.20 | transitive | " |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | 2.15.4 | transitive | " |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.15.4 | transitive | " |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | 2.15.4 | transitive | " |
| `org.apache.logging.log4j:log4j-api` | 2.21.1 | transitive | logging bridge |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.21.1 | transitive | " |
| `org.yaml:snakeyaml` | 2.2 | transitive | config parsing |
| `io.micrometer:micrometer-observation` | 1.12.5 | transitive | metrics |
| `io.micrometer:micrometer-commons` | 1.12.5 | transitive | " |
| `org.apiguardian:apiguardian-api` | 1.1.2 | transitive (test) | annotations |
| `org.opentest4j:opentest4j` | 1.3.0 | transitive (test) | test assertions |
| `org.jspecify:jspecify` | 1.0.0 | transitive (test) | annotations |

### MIT — `MIT.txt` (MaxiCP's own notice: `MaxiCP-MIT-UCLouvain.txt`)

| Artifact | Version | Direct? | Role |
|---|---|---|---|
| `com.github.aia-uclouvain:maxicp` | `3d5ec4b531` (commit, built by JitPack) | yes | **constraint-programming solver — the core engine** |
| `com.github.aia-uclouvain:solvercheck` | `-f8f6d239c0-1` | transitive | solver testing utilities |
| `org.xcsp:xcsp3-tools` | 2.5 | transitive | XCSP3 format tools (via MaxiCP) |
| `org.slf4j:slf4j-api` | 2.0.7 | transitive | logging facade |
| `org.slf4j:jul-to-slf4j` | 2.0.13 | transitive | logging bridge |

### Eclipse Public License 2.0 — `EPL-2.0.txt`

| Artifact | Version | Role |
|---|---|---|
| `org.junit.jupiter:junit-jupiter` (+ `-api`, `-params`, `-engine`) | 6.1.3 | test only |
| `org.junit.platform:junit-platform-commons`, `junit-platform-engine` | 6.1.3 | test only |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | dual EPL-2.0 / GPL-2.0 with Classpath Exception — elect EPL-2.0 |

### Eclipse Public License 1.0 — `EPL-1.0.txt`

| Artifact | Version | Role |
|---|---|---|
| `junit:junit` | 4.13.1 | transitive via MaxiCP → `xcsp3-tools` (compile scope, so it ships in the jar) |
| `ch.qos.logback:logback-classic` | 1.4.14 | logging — dual EPL-1.0 / LGPL-2.1 (`LGPL-2.1.txt`) |
| `ch.qos.logback:logback-core` | 1.4.14 | " |

### BSD 3-Clause — `BSD-3-Clause.txt`

| Artifact | Version | Role |
|---|---|---|
| `org.hamcrest:hamcrest-core` | 1.3 | transitive via `junit:junit` (see above) |

### CDDL 1.1 / GPL v2 dual — `CDDL-1.1.txt`

| Artifact | Version | Role |
|---|---|---|
| `javax.json:javax.json-api` | 1.1.2 | transitive via MaxiCP → xcsp3-tools; elect CDDL-1.1 |
| `org.glassfish:javax.json` | 1.1.2 | " |

### Public domain

| Artifact | Version | Role |
|---|---|---|
| `org.json:json` | 20231013 | transitive via MaxiCP. JSON-java has been in the public domain since its 2022 relicensing; older releases carried the JSON License and its "Good, not Evil" clause. |

---

## 2. Python libraries (experiment and plotting scripts, `src/main/python/`)

Not linked into the Java product; used to produce figures and to pre-process data.
No `requirements.txt` exists, so versions are whatever is installed locally.

| Library | Licence | File |
|---|---|---|
| matplotlib | Matplotlib License (PSF-derived, BSD-compatible) | `Matplotlib.txt` |
| numpy | BSD 3-Clause | `NumPy-BSD-3-Clause.txt` |
| opencv-python (`cv2`), used by `gsrPositionExtractor.py` only | MIT (wrapper); OpenCV itself Apache-2.0 | `opencv-python-MIT.txt`, `Apache-2.0.txt` |

---

## 3. Non-library third parties

| Party | What | Terms |
|---|---|---|
| **Groq, Inc.** | Hosted inference API, the default endpoint of the English → query translation (`LlmQuery.GROQ_ENDPOINT`). Proprietary SaaS, governed by Groq's terms of service. Query text leaves your infrastructure. Another OpenAI-compatible endpoint can be set with `LLM_ENDPOINT`. | Groq ToS |
| **OpenAI — gpt-oss-120b** | The default translation model (`LlmQuery.DEFAULT_MODEL = "openai/gpt-oss-120b"`), served by Groq; one of the three models of the evaluation. Not distributed with the software. | Apache 2.0 (`Apache-2.0.txt`) |
| **Alibaba — Qwen3-4B-Instruct-2507** | Evaluated through a self-hosted vLLM server. Not distributed with the software. | Apache 2.0 (`Apache-2.0.txt`) |
| **Anthropic — Claude** | Evaluated through Claude Code (`data/eval/tools/claude_shim.py`). Not distributed with the software. | Anthropic's terms of service |
| **SoccerNet** | The `gamestate-2024` dataset the system queries (not in the repository). Distributed under SoccerNet's own data agreement, accepted at download. | **Must be verified**, see §4 |
| **ball-action-spotting** ([lRomul/ball-action-spotting](https://github.com/lRomul/ball-action-spotting)) | `data/comparison/spotting/` holds the pass predictions of this model on the 164 clips, used for the comparison experiment. | See that repository, §4 |

---

## 4. Points requiring attention

**1 — MaxiCP is pinned to a commit, but one of its dependencies is not.**
`pom.xml` requests MaxiCP commit `3d5ec4b531`, built by JitPack, so the artifact and its licence
(MIT, © UCLouvain) are fixed. Its own POM still pulls `solvercheck` as the JitPack snapshot
`-f8f6d239c0-1`. `solvercheck` declares MIT in its POM but has **no LICENSE file** in its
repository; worth asking the authors to add one, as both are UCLouvain projects.

**2 — The SoccerNet dataset licence is the binding constraint for commercial use.**
The software is worthless without tracking data, and the data it was built on is
research-released. The `sn-gamestate` companion code is GPL-3.0 (not used here), but the
*dataset* is governed by a separate agreement accepted at download time, which is where
any non-commercial restriction would sit. This should be checked against the actual
agreement before any commercial claim — it is more likely to constrain the product than
anything in the software list above.

**3 — The action-spotting predictions are another project's output.**
`data/comparison/spotting/` redistributes the predictions of lRomul/ball-action-spotting on
SoccerNet clips. Check that repository's licence, and the SoccerNet agreement, before
publishing them.

**4 — Attribution obligations are triggered by distribution, not by use.**
The Spring Boot jar `TactiCP-1.0-web.jar` physically bundles every compile-scope artifact above.
If that jar is shipped to anyone, the Apache-2.0 (§4), MIT, BSD, EPL and CDDL notice
requirements all apply and this directory must travel with it. If the software is only ever
operated as a hosted service, those obligations largely do not arise.
