require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const cookieParser = require('cookie-parser');
const crypto = require('crypto');
const PlaidAdapter = require('./providers/plaidAdapter');

const app = express();
const PORT = process.env.PORT || 4000;

// Security Middleware
app.use(helmet());
app.use(cookieParser(process.env.SESSION_SECRET || 'fallback-secret-key-321'));
app.use(express.json({ limit: '1mb' }));

const clientOrigin = process.env.CLIENT_ORIGIN || '*';
app.use(cors({
  origin: clientOrigin,
  credentials: true,
}));

// Rate limiting: 100 requests per 15 minutes per IP
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests, please try again later.' },
});
app.use('/api/', apiLimiter);

// AES-256-GCM Encryption Helpers for storing tokens at rest
const ENCRYPTION_KEY = Buffer.from(
  (process.env.TOKEN_ENCRYPTION_KEY || '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef').slice(0, 64),
  'hex'
);

function encryptToken(plainText) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', ENCRYPTION_KEY, iv);
  let encrypted = cipher.update(plainText, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  const authTag = cipher.getAuthTag().toString('hex');
  return `${iv.toString('hex')}:${encrypted}:${authTag}`;
}

function decryptToken(cipherString) {
  const [ivHex, encryptedHex, authTagHex] = cipherString.split(':');
  const iv = Buffer.from(ivHex, 'hex');
  const decipher = crypto.createDecipheriv('aes-256-gcm', ENCRYPTION_KEY, iv);
  decipher.setAuthTag(Buffer.from(authTagHex, 'hex'));
  let decrypted = decipher.update(encryptedHex, 'hex', 'utf8');
  decrypted += decipher.final('utf8');
  return decrypted;
}

// In-Memory store for demonstration (in production replace with PostgreSQL/Firestore)
const userVault = new Map(); // userId -> { encryptedAccessToken, itemId, institutionName, lastSync, auditLog: [] }

const plaid = new PlaidAdapter();

// Helper: Get or create authenticated user session from secure cookie
function getSessionUser(req, res) {
  let userId = req.signedCookies?.growmint_uid;
  if (!userId) {
    userId = 'user_' + crypto.randomUUID();
    res.cookie('growmint_uid', userId, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 60 * 1000, // 30 minute inactivity timeout
      signed: true,
    });
  }
  return userId;
}

// 1. Session Auth Status
app.get('/api/auth/session', (req, res) => {
  const userId = getSessionUser(req, res);
  const vault = userVault.get(userId);
  res.json({
    userId,
    isLinked: Boolean(vault?.encryptedAccessToken),
    institutionName: vault?.institutionName || null,
    lastSync: vault?.lastSync || null,
  });
});

// 2. Create Plaid Link Token (Read-Only Scopes)
app.post('/api/link/token/create', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const webhookUrl = `${req.protocol}://${req.get('host')}/api/bank/webhook`;
    const tokenData = await plaid.createLinkToken(userId, webhookUrl);
    res.json(tokenData);
  } catch (error) {
    console.error('Link token creation failed:', error.message);
    res.status(500).json({ error: 'Failed to initialize bank link session.' });
  }
});

// 3. Exchange Public Token & Encrypt Access Token at rest
app.post('/api/link/token/exchange', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const { publicToken, institutionName } = req.body;

    if (!publicToken) {
      return res.status(400).json({ error: 'Missing publicToken parameter.' });
    }

    const { accessToken, itemId } = await plaid.exchangePublicToken(publicToken);
    const encryptedToken = encryptToken(accessToken);

    const vaultEntry = userVault.get(userId) || { auditLog: [] };
    vaultEntry.encryptedAccessToken = encryptedToken;
    vaultEntry.itemId = itemId;
    vaultEntry.institutionName = institutionName || 'Linked Bank';
    vaultEntry.lastSync = new Date().toISOString();
    vaultEntry.auditLog.push({
      timestamp: Date.now(),
      action: 'LINKED',
      description: `Linked ${vaultEntry.institutionName} via hosted read-only consent flow.`,
    });
    userVault.set(userId, vaultEntry);

    res.json({
      success: true,
      message: 'Bank linked securely. Access token encrypted at rest.',
      institutionName: vaultEntry.institutionName,
    });
  } catch (error) {
    console.error('Token exchange error:', error.message);
    res.status(500).json({ error: 'Failed to complete bank linking handshake.' });
  }
});

// 4. Fetch Bank Balances (Masked Account Numbers)
app.get('/api/bank/accounts', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const vault = userVault.get(userId);
    if (!vault?.encryptedAccessToken) {
      return res.status(404).json({ error: 'No bank account linked for this session.' });
    }

    const accessToken = decryptToken(vault.encryptedAccessToken);
    const accounts = await plaid.getAccounts(accessToken);
    vault.lastSync = new Date().toISOString();

    res.json({
      institutionName: vault.institutionName,
      lastSync: vault.lastSync,
      accounts,
    });
  } catch (error) {
    console.error('Account balance fetch error:', error.message);
    res.status(500).json({ error: 'Unable to retrieve bank balances.' });
  }
});

// 5. Fetch Read-Only Transactions
app.get('/api/bank/transactions', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const vault = userVault.get(userId);
    if (!vault?.encryptedAccessToken) {
      return res.status(404).json({ error: 'No bank account linked for this session.' });
    }

    const accessToken = decryptToken(vault.encryptedAccessToken);
    const now = new Date();
    const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    const startDate = thirtyDaysAgo.toISOString().split('T')[0];
    const endDate = now.toISOString().split('T')[0];

    const transactions = await plaid.getTransactions(accessToken, startDate, endDate);
    res.json({ transactions });
  } catch (error) {
    console.error('Transaction fetch error:', error.message);
    res.status(500).json({ error: 'Unable to retrieve transaction history.' });
  }
});

// 6. Manual & Webhook Sync Refresh
app.post('/api/bank/refresh', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const vault = userVault.get(userId);
    if (!vault?.encryptedAccessToken) {
      return res.status(404).json({ error: 'No bank account linked.' });
    }

    const accessToken = decryptToken(vault.encryptedAccessToken);
    const accounts = await plaid.getAccounts(accessToken);
    const now = new Date();
    const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    const transactions = await plaid.getTransactions(
      accessToken,
      thirtyDaysAgo.toISOString().split('T')[0],
      now.toISOString().split('T')[0]
    );

    vault.lastSync = now.toISOString();
    vault.auditLog.push({
      timestamp: Date.now(),
      action: 'SYNC_REFRESH',
      description: `Refreshed ${accounts.length} accounts and ${transactions.length} recent transactions.`,
    });

    res.json({
      success: true,
      lastSync: vault.lastSync,
      accounts,
      transactions,
    });
  } catch (error) {
    console.error('Bank refresh error:', error.message);
    res.status(500).json({ error: 'Failed to refresh bank data.' });
  }
});

// 7. Disconnect and Delete All Stored Bank Data
app.post('/api/bank/disconnect', async (req, res) => {
  try {
    const userId = getSessionUser(req, res);
    const vault = userVault.get(userId);
    if (vault?.encryptedAccessToken) {
      try {
        const accessToken = decryptToken(vault.encryptedAccessToken);
        await plaid.removeItem(accessToken);
      } catch (err) {
        console.warn('Provider item revoke warning:', err.message);
      }
    }

    // Completely erase stored data for this user
    userVault.delete(userId);

    res.json({
      success: true,
      message: 'Bank connection revoked. All stored bank data and encrypted credentials permanently purged.',
    });
  } catch (error) {
    console.error('Disconnect error:', error.message);
    res.status(500).json({ error: 'Failed to disconnect bank account.' });
  }
});

// 8. Connection Audit Log Endpoint
app.get('/api/bank/audit-log', (req, res) => {
  const userId = getSessionUser(req, res);
  const vault = userVault.get(userId);
  res.json({
    auditLog: vault?.auditLog || [],
  });
});

// 9. Provider Webhook Endpoint with HMAC signature check
app.post('/api/bank/webhook', (req, res) => {
  const signature = req.headers['plaid-verification'];
  // Verify webhook signature if secret configured
  if (process.env.PLAID_WEBHOOK_SECRET && signature) {
    const hmac = crypto.createHmac('sha256', process.env.PLAID_WEBHOOK_SECRET);
    hmac.update(JSON.stringify(req.body));
    const digest = hmac.digest('hex');
    if (digest !== signature) {
      return res.status(401).json({ error: 'Invalid webhook signature.' });
    }
  }

  const { webhook_type, webhook_code, item_id } = req.body;
  console.log(`Received provider webhook: ${webhook_type} (${webhook_code}) for item ${item_id}`);

  // In production, trigger background job to refresh user balance
  res.status(200).json({ status: 'received' });
});

app.listen(PORT, () => {
  console.log(`GrowMint Secure Banking Backend running on port ${PORT}`);
});
