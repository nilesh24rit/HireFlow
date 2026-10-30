<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:00ff9c,100:0d1117&height=180&section=header&text=HireFlow&fontSize=60&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Microservices-based%20Hiring%20%26%20Recruitment%20Platform&descAlignY=58&descSize=18" alt="HireFlow header" />

<a href="https://github.com/nilesh24rit/HireFlow">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=22&duration=3000&pause=800&color=00FF9C&center=true&vCenter=true&width=720&lines=Hey+%F0%9F%91%8B+I'm+Nilesh+Kumar+Singh;I+turn+coffee+%E2%98%95+into+Spring+Boot+microservices;I+break+things+in+Docker+so+prod+doesn't+have+to;gRPC+%E2%86%92+Kafka+%E2%86%92+Redis+%E2%86%92+PostgreSQL+%E2%86%92+%E2%9C%A8;Looking+for+a+Backend+SDE+Internship+%F0%9F%9A%80" alt="Typing SVG" />
</a>

<br/>

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![gRPC](https://img.shields.io/badge/gRPC-244c5a?style=for-the-badge&logo=google&logoColor=white)

![Profile views](https://komarev.com/ghpvc/?username=nilesh24rit&label=Profile+views&color=00ff9c&style=flat-square)
![Status](https://img.shields.io/badge/Status-Open_to_Internships-00ff9c?style=flat-square)
![College](https://img.shields.io/badge/B.Tech_CSE-MSRIT_Bengaluru-blue?style=flat-square)
![License](https://img.shields.io/badge/License-Apache_2.0-orange?style=flat-square)

</div>

---

## 📌 Table of Contents

- [✨ About HireFlow](#-about-hireflow)
- [🚀 Features](#-features)
- [🏗️ Architecture](#️-architecture)
- [🧩 Services](#-services)
- [🛠️ Tech Stack](#️-tech-stack)
- [📂 Project Structure](#-project-structure)
- [⚡ Getting Started](#-getting-started)
- [🧠 `./about-me.java`](#-about-mejava)
- [🛠️ Arsenal](#️-arsenal)
- [🔥 Featured Projects](#-featured-projects)
- [📊 Stats](#-stats-aka-proof-i-touch-grass-er-keyboards)
- [🎯 Currently](#-currently)
- [🤝 Let's connect](#-lets-connect)
- [📄 License](#-license)

---

## ✨ About HireFlow

**HireFlow** is a backend-first, **microservices-based hiring platform** that models the complete recruitment lifecycle - from posting a job, to candidates applying, to AI-assisted screening, analytics and real-time notifications.

Every capability lives in its own independently deployable service. Services talk to each other through **gRPC** (synchronous, contract-first with Protobuf) and **Kafka** (asynchronous, event-driven), all behind a single **API Gateway**.

> 💡 Built to practise what production systems actually look like: service boundaries, contracts, events, caching, containers.

---

## 🚀 Features

| | Feature | Description |
|---|---|---|
| 🔐 | **Authentication & Authorization** | Secure login, token-based access and role handling via a dedicated auth service |
| 💼 | **Job Management** | Create, update, publish and search job postings |
| 🧑‍💻 | **Candidate Profiles** | Manage candidate information and profiles |
| 📝 | **Application Tracking** | Candidates apply to jobs; track each application through the hiring pipeline |
| 🤖 | **AI Assistance** | Dedicated AI service for smart screening / matching |
| 📈 | **Analytics** | Event-driven analytics on jobs, applications and funnel metrics |
| 🔔 | **Notifications** | Async notifications triggered by hiring events |
| 🚪 | **Single Entry Point** | API Gateway for routing and cross-cutting concerns |
| 📜 | **Contract-first APIs** | Shared Protobuf / gRPC contracts across services |
| 🌱 | **Dev Seed Data** | Ready-made seed data for quick local development |

---

## 🏗️ Architecture

```mermaid
flowchart LR
    Client([🌐 Client]) --> GW[🚪 API Gateway]

    GW --> AUTH[🔐 auth-service]
    GW --> JOB[💼 job-service]
    GW --> CAND[🧑‍💻 candidate-service]
    GW --> APP[📝 application-service]

    APP -. gRPC .-> JOB
    APP -. gRPC .-> CAND
    APP -. gRPC .-> AI[🤖 ai-service]

    APP == events ==> K{{Kafka}}
    JOB == events ==> K
    K ==> NOTIF[🔔 notification-service]
    K ==> ANA[📈 analytics-service]

    AUTH --- PG[(PostgreSQL)]
    JOB --- PG
    CAND --- PG
    APP --- PG
    GW --- R[(Redis)]
```

> ℹ️ The diagram shows the intended service topology. Adjust the arrows if your actual wiring differs.

---

## 🧩 Services

| Service | Responsibility |
|---|---|
| 🚪 [`api-gateway`](./api-gateway) | Single entry point - routing, filtering and cross-cutting concerns |
| 🔐 [`auth-service`](./auth-service) | Authentication, authorization and user identity |
| 💼 [`job-service`](./job-service) | Job postings lifecycle |
| 🧑‍💻 [`candidate-service`](./candidate-service) | Candidate profiles and data |
| 📝 [`application-service`](./application-service) | Job applications and hiring pipeline stages |
| 🤖 [`ai-service`](./ai-service) | AI-powered screening and matching |
| 📈 [`analytics-service`](./analytics-service) | Consumes events and produces hiring analytics |
| 🔔 [`notification-service`](./notification-service) | Sends notifications on hiring events |
| 📜 [`proto`](./proto) | Protobuf definitions |
| 🔗 [`grpc-contracts`](./grpc-contracts) | Shared generated gRPC contracts |
| 🌱 [`dev-seed`](./dev-seed) | Seed data / scripts for local development |

---

## 🛠️ Tech Stack

| Layer | Technologies |
|---|---|
| **Language & Framework** | `Java` `Spring Boot` `REST APIs` |
| **Inter-service communication** | `gRPC` `Protobuf` `Kafka` |
| **Data & Caching** | `PostgreSQL` `Redis` |
| **Infra & DevOps** | `Docker` `Git` `GitHub Actions` |
| **Observability** | `Prometheus` `Grafana` |

---

## 📂 Project Structure

```text
HireFlow/
├── 🚪 api-gateway/            # Entry point & routing
├── 🔐 auth-service/           # Authentication & authorization
├── 💼 job-service/            # Job postings
├── 🧑‍💻 candidate-service/      # Candidate profiles
├── 📝 application-service/    # Applications & pipeline
├── 🤖 ai-service/             # AI screening / matching
├── 📈 analytics-service/      # Analytics (event consumer)
├── 🔔 notification-service/   # Notifications
├── 📜 proto/                  # .proto definitions
├── 🔗 grpc-contracts/         # Shared gRPC contracts
├── 🌱 dev-seed/               # Local dev seed data
├── LICENSE                    # Apache-2.0
└── README.md
```

---

## ⚡ Getting Started

### Prerequisites

- ☕ Java 17+
- 🐳 Docker & Docker Compose
- 📦 Maven / Gradle (as used by each service)
- 🐘 PostgreSQL, 🔴 Redis, 📨 Kafka (run via Docker)

### Run locally

```bash
# 1. Clone the repository
git clone https://github.com/nilesh24rit/HireFlow.git
cd HireFlow

# 2. Start infrastructure (PostgreSQL, Redis, Kafka)
docker compose up -d

# 3. Start each service (example)
cd auth-service && ./mvnw spring-boot:run

# 4. (Optional) Load development seed data
#    see ./dev-seed for scripts
```

> 🔧 Replace the commands above with your exact setup (compose file, ports, env variables) as the project evolves.

---

<div align="center">

# 👨‍💻 About the Author

</div>

<div align="center">

[![](https://github.com/nilesh24rit/nilesh24rit/raw/main/header-contributions.svg)](https://github.com/nilesh24rit)

[![](https://github.com/nilesh24rit/nilesh24rit/raw/main/contrib-heatmap.svg)](https://github.com/nilesh24rit)

[![](https://github.com/nilesh24rit/nilesh24rit/raw/main/header-whoami.svg)](https://github.com/nilesh24rit)

| [![](https://github.com/nilesh24rit/nilesh24rit/raw/main/nilesh-ascii.svg)](https://github.com/nilesh24rit) | [![](https://github.com/nilesh24rit/nilesh24rit/raw/main/info-card.svg)](https://github.com/nilesh24rit) |
|---|---|

</div>

---

## 🧠 `./about-me.java`

```java
public class Nilesh {

    String name      = "Nilesh Kumar Singh";
    String college   = "M. S. Ramaiah Institute of Technology, Bengaluru";
    String degree    = "B.Tech CSE (pre-final year)";
    String superpower = "Making backends scale (and Dockerfiles stop crying)";

    String[] stack = {
        "Java", "Spring Boot", "REST APIs", "Microservices",
        "PostgreSQL", "Redis", "Kafka", "gRPC", "Docker"
    };

    String currentlyHunting = "Backend SWE internships 🎯";
    String longTermQuest    = "Ship code in Japan 🇯🇵";
    String funFact          = "I read stack traces for fun. Mostly out of necessity.";

    public void run() {
        while (alive) {
            eat(); sleep(); code(); deploy(); debug(); repeat();
        }
    }
}
```

---

## 🛠️ Arsenal

<div align="center">

[![My Skills](https://skillicons.dev/icons?i=java,spring,postgres,redis,kafka,mongodb,docker,git,github,githubactions,grafana,prometheus,linux,idea&perline=7)](https://skillicons.dev)

</div>

---

## 🔥 Featured Projects

| Project | What it does | Stack |
|---|---|---|
| 🛒 [**CommerceX**](https://github.com/nilesh24rit/E-Commerce) | Production-style e-commerce backend, live on Render, with monitoring dashboards | `Spring Boot` `PostgreSQL` `Redis` `Prometheus` `Grafana` |
| 🏥 [**Patient-Manager**](https://github.com/nilesh24rit/Patient-Manager) | Microservices system: patient, billing (gRPC), analytics (Kafka + Protobuf) behind an API gateway | `Spring Cloud Gateway` `gRPC` `Kafka` `Docker` |
| 🛡️ [**CacheGuard**](https://github.com/nilesh24rit/CacheGuard) | Redis-powered rate limiting for Spring Boot apps | `Spring Boot` `Redis` |
| 📓 [**JournalApp**](https://github.com/nilesh24rit/JournalApp) | Secure journaling backend with sessions, Kafka events and cloud-hosted DBs | `Spring Security` `MongoDB` `Redis` `Kafka` |
| 🧮 [**dsa-toolkit-java**](https://github.com/nilesh24rit/dsa-toolkit-java) | Modular DSA library: graph algorithms, sorting, self-balancing trees | `Java` `DSA` |

---

## 📊 Stats (a.k.a. proof I touch grass... er, keyboards)

<div align="center">

[![Nilesh's GitHub stats](https://github-readme-stats.vercel.app/api?username=nilesh24rit&show_icons=true&theme=radical&hide_border=true&bg_color=0d1117)](https://github.com/nilesh24rit)
[![Top Languages](https://github-readme-stats.vercel.app/api/top-langs/?username=nilesh24rit&layout=compact&theme=radical&hide_border=true&bg_color=0d1117)](https://github.com/nilesh24rit)

[![GitHub Streak](https://github-readme-streak-stats.herokuapp.com/?user=nilesh24rit&theme=radical&hide_border=true&background=0d1117)](https://github.com/nilesh24rit)

</div>

---

## 🎯 Currently

- 🔭 Building & breaking **microservices** (and fixing them at 2 AM)
- 🧗 Grinding **DSA** alongside backend projects
- 📬 Open to **backend software engineering internships**
- 🇯🇵 Long-term goal: working in the **Japanese tech scene**

---

## 🤝 Let's connect

<div align="center">

[![GitHub](https://img.shields.io/badge/GitHub-nilesh24rit-181717?style=for-the-badge&logo=github)](https://github.com/nilesh24rit)

*"It works on my machine" - me, right before Docker humbled me. 🐳*

</div>

---

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](./LICENSE) file for details.

<div align="center">

⭐ **If you like HireFlow, drop a star - it keeps the Kafka topics flowing!** ⭐

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0d1117,100:00ff9c&height=120&section=footer" alt="footer" />

</div>
