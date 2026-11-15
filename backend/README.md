# GrowMint Secure Read-Only Banking Backend

This backend module handles secure bank data aggregation for **GrowMint**. It protects user banking credentials, isolates provider API secrets, and strictly adheres to zero-trust read-only access.

---

## Security Architecture

1. **Secrets Isolation**: Plaid client ID and API secrets live exclusively on this backend server. Client Android apps never receive or touch provider secrets.
2. **Token Encryption at Rest**: Access tokens returned by Open Banking providers are encrypted with **AES-256-GCM** using a 32-byte master key before storage.
3. **Read-Only Scopes**: Only `transactions` and account balance read scopes are requested. Payments, fund transfers, and identity writes are strictly barred.
4. **Session Security**: Authenticated requests use HttpOnly signed cookies with a 30-minute inactivity timeout.
5. **Masked Identifiers**: Account numbers are masked everywhere (only the last 4 digits, e.g. `•••• 4821`).
6. **Webhook Verification**: Validates provider webhook HMAC signatures to prevent spoofing.

---

## API Endpoints

- `GET  /api/auth/session` - Returns session status and link state
- `POST /api/link/token/create` - Creates read-only Plaid link token
- `POST /api/link/token/exchange` - Exchanges public token for encrypted access token
- `GET  /api/bank/accounts` - Retrieves masked accounts and real-time balances
- `GET  /api/bank/transactions` - Retrieves categorized read-only transactions
- `POST /api/bank/refresh` - Triggers sync refresh
- `POST /api/bank/disconnect` - Revokes provider item and permanently purges user data
- `GET  /api/bank/audit-log` - Returns connection audit log
- `POST /api/bank/webhook` - Receives incoming transaction & balance webhooks

---

## Deployment Instructions

### 1. Local Testing
```bash
cd backend
npm install
cp .env.example .env
npm start
```

### 2. Docker / Cloud Run Deployment
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 4000
CMD ["node", "server.js"]
```
Deploy to Google Cloud Run, Render, or Railway and configure environment variables in your hosting provider's dashboard.
