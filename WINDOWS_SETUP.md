# Windows setup

## 1. Install prerequisites
1. **Java 21**: install Eclipse Temurin 21 (MSI) with "Set JAVA_HOME" and "Add to PATH" selected.
   Check: `java -version` shows 21.
2. **Node.js 20 or 22 LTS** (only needed to build the frontend).
3. **PostgreSQL**, one of:
   - **Docker Desktop** (recommended): nothing else to install, because `docker-compose.yml` runs PostgreSQL 16
     bound to `127.0.0.1:5432`.
   - **Manual PostgreSQL 16** (EDB installer): then in `psql` as the `postgres` user:
     ```sql
     CREATE USER c2c WITH PASSWORD 'choose-a-strong-password';
     CREATE DATABASE c2c_agent OWNER c2c;
     ```
     Set `C2C_DB_MODE=manual` and `C2C_DB_PASSWORD=...` in `.env`.
4. Git (to clone the repository).

## 2. Install
```powershell
git clone https://github.com/SaiPrakashBV/AI-Powered-C2C-Job-Application-Agent.git
cd AI-Powered-C2C-Job-Application-Agent
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```
`setup.ps1` checks Java and Node, creates `.env` with a random database password, starts PostgreSQL (Docker
mode), builds the frontend and backend, and copies the runnable jar to `run\c2c-agent.jar`.

## 3. Run
- Double-click **`Start-C2CAgent.cmd`**. It checks PostgreSQL, starts the backend hidden, waits for the health
  check and opens http://localhost:8080.
- The first visit shows **first-time setup**, where you create your owner account (password ≥ 12 characters).
- **Lock** (top right) logs out. Sessions expire after the timeout set in Settings (default 60 min).
- `Stop-C2CAgent.cmd` stops the app. `scripts\stop.ps1 -IncludeDatabase` also stops the PostgreSQL container.
- `scripts\status.ps1` shows process, health and database status.

To create a desktop shortcut, right-click `Start-C2CAgent.cmd` → Send to → Desktop.

## Configuration (`.env`)
| Variable | Default | Meaning |
|---|---|---|
| `C2C_DB_MODE` | docker | `docker` or `manual` |
| `C2C_DB_URL` / `C2C_DB_USER` / `C2C_DB_PASSWORD` / `C2C_DB_PORT` | localhost:5432, c2c | Database connection |
| `C2C_PORT` | 8080 | Dashboard port |
| `C2C_BIND_ADDRESS` | 127.0.0.1 | Keep loopback unless you know why |
| `C2C_DATA_DIR` | `%USERPROFILE%\C2CAgent\data` | Resumes, attachments, logs, backups |
| `C2C_JAVA_OPTS` | -Xmx768m | JVM options |

Logs: `%USERPROFILE%\C2CAgent\data\logs\c2c-agent.log` (and `console.err.log` for startup failures).

## Troubleshooting
| Symptom | Fix |
|---|---|
| "Java 21 or newer is required" | Install Temurin 21, reopen PowerShell |
| "PostgreSQL is not reachable" | Start Docker Desktop (wait until it shows "running"), or start your PostgreSQL service |
| "Port 8080 is already in use" | Set `C2C_PORT=8081` in `.env` |
| Script blocked by execution policy | Use the `.cmd` launchers, or `powershell -ExecutionPolicy Bypass -File ...` |
| App exits during startup | Read `console.err.log` / `c2c-agent.log` in the data `logs` folder; a common cause is a wrong DB password in `.env` |
| Locked out after failed logins | Wait 15 minutes (lockout duration) |

> These scripts were written and reviewed but not yet executed on Windows (see PROGRESS.md). Please report any
> error output so it can be fixed.
