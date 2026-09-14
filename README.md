# Reminder App

A small learning project inspired by Apple Reminders. It has a Spring Boot REST API and a Vue 3 frontend for managing a simple list of reminders.

## Current features

- List reminders
- Create reminders with a required title and optional due date
- Edit a reminder's title and due date
- Mark reminders complete or incomplete
- Delete reminders
- Validate due dates: optional, today or later, with years from 1000 to 9999

## Tech stack

- Java 21, Spring Boot, Maven
- Spring Web, Spring Data JPA, Bean Validation
- H2 file database for local development
- Vue 3 Composition API, Vite, and browser `fetch()`

## Project structure

```text
reminder-app/
├── backend/        Spring Boot API and Maven project
├── frontend/       Vue 3 + Vite application
└── README.md
```

## Run the backend

Prerequisites: Java 21 and Maven.

```bash
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. The current development database is H2 and is stored locally under `backend/data/` when the backend is started from the `backend` directory.

To run the backend tests:

```bash
mvn test
```

## Run the frontend

Prerequisites: Node.js and npm.

In a second terminal, after starting the backend:

```bash
cd frontend
npm install
npm run dev
```

Vite normally serves the frontend at `http://localhost:5173`. Its development proxy forwards `/api` requests to the backend at `http://localhost:8080`.

## REST API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/reminders` | List reminders |
| `POST` | `/api/reminders` | Create a reminder |
| `PUT` | `/api/reminders/{id}` | Update title and due date |
| `PATCH` | `/api/reminders/{id}/completion` | Set completion status |
| `DELETE` | `/api/reminders/{id}` | Delete a reminder (`204 No Content`) |

Create and update requests use a required `title` and an optional `dueDate` in `YYYY-MM-DD` format (or `null`). Completion requests use `{ "completed": true }` or `{ "completed": false }`.

## Project status

This is a small local v1 project with a working reminder API and frontend. Reasonable next steps are improving the interface and adding frontend tests. Features such as authentication, notifications, deployment, Docker, Redis, and a production database are not part of the current project.
