# Reminder App

A small learning project inspired by Apple Reminders. It has a Spring Boot REST API and a Vue 3 frontend for managing a simple list of reminders.

**V8C adds registration, sign-in, and session restoration in Vue.** Each account has its own lists, reminders, search results, pages, and counts.

## Current features

- List reminders
- Create reminders with a required title and optional due date
- Edit a reminder's title and due date
- Mark reminders complete or incomplete
- Delete reminders
- Validate due dates: optional, today or later, with years from 1000 to 9999

## Tech stack

- Java 21, Spring Boot, Maven
- Spring Web, Spring Data JPA, Spring Security, Bean Validation
- PostgreSQL for local development; H2 in-memory database for backend tests
- Vue 3 Composition API, Vite, and browser `fetch()`

## Project structure

```text
reminder-app/
├── backend/        Spring Boot API and Maven project
├── frontend/       Vue 3 + Vite application
└── README.md
```

## Run the backend

Prerequisites: Java 21, Maven, and a local PostgreSQL server with a `reminder_app` database.

```bash
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. The development configuration connects to PostgreSQL at `localhost:5432` using the `reminder_app` database and the local `zhangzhengyang` user. Backend integration tests use a separate in-memory H2 database instead.

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

Run the frontend request and session tests from the frontend directory with `node --test test/sessionApi.test.js`.

## REST API

Reminder and list requests use the account from the authenticated session. A resource belonging to another account or a legacy unowned list returns the same 404 as a missing resource. Global search covers all lists for the signed-in account.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/reminders` | List reminders |
| `POST` | `/api/reminders` | Create a reminder |
| `PUT` | `/api/reminders/{id}` | Update title and due date |
| `PATCH` | `/api/reminders/{id}/completion` | Set completion status |
| `DELETE` | `/api/reminders/{id}` | Delete a reminder (`204 No Content`) |

Create and update requests use a required `title` and an optional `dueDate` in `YYYY-MM-DD` format (or `null`). Completion requests use `{ "completed": true }` or `{ "completed": false }`.

## Authentication API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Start a session if needed and return a CSRF header name and token |
| `POST` | `/api/auth/register` | Register with JSON `email` and `password` |
| `POST` | `/api/auth/login` | Sign in with JSON `email` and `password` |
| `GET` | `/api/auth/me` | Return the signed-in user's ID, email, and creation time |
| `POST` | `/api/auth/logout` | End the authenticated session |

Email addresses are stripped and lowercased using `Locale.ROOT`. Registration requires at least 12 characters and at most 72 UTF-8 bytes for BCrypt. Registration and login are separate steps.

Keep the session cookie and send the returned CSRF token in the named header for each POST. Fetch a fresh token after login and after logout. The Vite proxy keeps development requests same-origin. Session cookies use HttpOnly and SameSite=Lax. The shared configuration requires HTTPS for the cookie, and the local dev profile allows HTTP. Set `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` if using the dev profile over HTTPS.

## List ownership and legacy data

Flyway V6 creates the users table. V7 adds the list owner foreign key, the default-list marker, and lookup indexes. V7 creates a new default list for each account already registered in V8A. New registrations create the account and default list in one transaction. The list API returns `isDefault`, which determines the default list even if someone renames it or creates another list called Reminders.

Pre-V8 lists retain a null owner. Their reminders stay in those lists, and all application queries exclude them. To transfer legacy data, first back up the database and stop application writes. Review an explicit mapping of legacy list IDs to existing user IDs with the intended owners. In a separate administrative transaction, assign only those reviewed lists that still have a null owner and verify the affected rows before committing. Keep the transferred lists non-default so each account's existing default is preserved. Review access with the intended accounts after the transfer.

## Verify V8C locally

1. With the intended local PostgreSQL database backed up, confirm Flyway V1–V7 are applied. Start the backend and open the app through Vite.
2. Register Alice, then sign in explicitly. Create a list and a reminder in it. Check the default list and sidebar counts, then sign out.
3. Register or sign in as Bob. Confirm Alice's list and reminder are absent from lists, global search, and counts. Create a reminder for Bob, then sign out.
4. Sign back in as Alice. Confirm Alice's data is present and Bob's is absent. Refresh the browser and confirm that Alice's session restores. Try both search scopes and page navigation with enough matching reminders to span two pages.
5. In browser developer tools, invalidate the session cookie or let the session expire. Trigger a protected read and confirm that the app returns to sign-in. For CSRF, inspect a write request for the configured header. A test write with the header omitted or invalid should be rejected. After signing in again, verify that writes use a fresh token.
6. Throttle network requests, start a search as Alice, then sign out and sign in as Bob. Confirm that only Bob's data appears when the pending request finishes. Check the form with Tab and Enter at a narrow viewport.
7. If the database contains unowned legacy lists, confirm that neither account can see those lists or their reminders. Leave those records in place.
