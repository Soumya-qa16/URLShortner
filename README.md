# URL Shortener & SDLC Orchestration Engine

This project contains a two-part engineering deliverable: a hardened Spring Boot URL Shortener service and a generic, reusable agentic orchestration engine that coordinates a full SDLC lifecycle (requirements, design, implementation, testing, documentation, and release).

## 🚀 Features

* **URL Shortener API:** Supports creating short links with optional custom aliases and expirations, processes redirects, and asynchronously tracks click analytics.
* **Agentic Orchestration Engine:** A robust engine featuring sequential and parallel execution, bounded retries, dynamic re-planning, rollbacks, and human approval checkpoints for high-impact actions.
* **Caching & Persistence:** Utilizes a Redis read-through cache and a PostgreSQL-backed persistence layer.
* **Comprehensive Testing:** Includes a 64-method unit test suite across 16 packages covering both the application and the orchestration layer.

## 🛠️ Tech Stack

* **Language & Framework:** Java 17, Spring Boot 3.2
* **Build Tool:** Maven 3.8+
* **Data & Caching:** PostgreSQL, Redis

## 🚦 Getting Started

### 📋 Prerequisites

To run the application and demo, you will need:

* **Java 17**
* **Maven 3.8+**
* **PostgreSQL** (Required for the application; not required to run tests or the orchestration demo)
* **Redis** (Required for the application's cache; not required to run tests or the orchestration demo)

## ⚙️ Build & Run the Application

### 🌱 Run the Spring Boot Application

```bash
mvn clean package
mvn spring-boot:run
```
### 🤖 Run Orchestration Demo

This standalone entry point runs all three SDLC scenarios (greenfield, brownfield, ambiguous) end-to-end and prints final stage statuses, reliability metrics, and the full audit trail:

```bash
mvn compile exec:java -Dexec.mainClass=com.example.orchestration.demo.OrchestrationDemo
```
### 🧪 Run the Test Suite

Execute the comprehensive, dependency-free test suite:

```bash
mvn test
```
