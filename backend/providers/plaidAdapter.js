/**
 * Plaid Provider Adapter for GrowMint
 * Implements strict READ-ONLY scopes (balances, transactions).
 * NEVER requests payments, transfers, or identity write permissions.
 */
const { Configuration, PlaidApi, PlaidEnvironments, Products, CountryCode } = require('plaid');

class PlaidAdapter {
  constructor() {
    const environment = process.env.PLAID_ENV || 'sandbox';
    const configuration = new Configuration({
      basePath: PlaidEnvironments[environment] || PlaidEnvironments.sandbox,
      baseOptions: {
        headers: {
          'PLAID-CLIENT-ID': process.env.PLAID_CLIENT_ID || 'test_client_id',
          'Plaid-Secret': process.env.PLAID_SECRET || 'test_secret',
        },
      },
    });
    this.client = new PlaidApi(configuration);
  }

  /**
   * Create Link Token with read-only scopes
   */
  async createLinkToken(userId, webhookUrl) {
    const request = {
      user: { client_user_id: userId },
      client_name: 'GrowMint Personal Finance',
      products: [Products.Transactions], // READ-ONLY transactions & balances only
      country_codes: [CountryCode.Us, CountryCode.Ca, CountryCode.Gb],
      language: 'en',
      webhook: webhookUrl || undefined,
    };
    const response = await this.client.linkTokenCreate(request);
    return response.data;
  }

  /**
   * Exchange Public Token from Link UI for Access Token
   */
  async exchangePublicToken(publicToken) {
    const response = await this.client.itemPublicTokenExchange({
      public_token: publicToken,
    });
    return {
      accessToken: response.data.access_token,
      itemId: response.data.item_id,
    };
  }

  /**
   * Get balances with masked account numbers
   */
  async getAccounts(accessToken) {
    const response = await this.client.accountsBalanceGet({
      access_token: accessToken,
    });

    return response.data.accounts.map((acc) => ({
      id: acc.account_id,
      name: acc.name,
      officialName: acc.official_name,
      mask: acc.mask ? `•••• ${acc.mask}` : '•••• 0000', // STRICT MASKING
      type: acc.type,
      subtype: acc.subtype,
      currentBalance: acc.balances.current || 0,
      availableBalance: acc.balances.available || acc.balances.current || 0,
      currency: acc.balances.iso_currency_code || 'USD',
    }));
  }

  /**
   * Get transactions for sync
   */
  async getTransactions(accessToken, startDate, endDate) {
    const response = await this.client.transactionsGet({
      access_token: accessToken,
      start_date: startDate,
      end_date: endDate,
      options: { count: 100, offset: 0 },
    });

    return response.data.transactions.map((tx) => ({
      id: tx.transaction_id,
      accountId: tx.account_id,
      amount: tx.amount, // positive = expense, negative = income in Plaid
      currency: tx.iso_currency_code || 'USD',
      category: (tx.category && tx.category[0]) || 'General',
      date: tx.date,
      pending: tx.pending || false,
      name: tx.name,
      merchantName: tx.merchant_name || tx.name,
    }));
  }

  /**
   * Revoke access token and remove item on provider
   */
  async removeItem(accessToken) {
    try {
      await this.client.itemRemove({ access_token: accessToken });
      return true;
    } catch (e) {
      console.warn('Plaid itemRemove warning:', e.message);
      return false;
    }
  }
}

module.exports = PlaidAdapter;
