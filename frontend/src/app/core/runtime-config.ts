/**
 * Environment-specific settings, served as /config.json next to the SPA. The same build is promoted from staging
 * to production: only this file differs (it is written at deploy time).
 */
export interface RuntimeConfig {
  oidc: {
    /** Cognito: https://cognito-idp.eu-west-3.amazonaws.com/<user-pool-id> */
    authority: string;
    clientId: string;
    scope: string;
    /**
     * Cognito does not publish an end_session_endpoint: its logout URL must be given explicitly,
     * e.g. https://<domain>.auth.eu-west-3.amazoncognito.com/logout?client_id=<id>&logout_uri=<origin>
     * Null when the provider supports standard OIDC logout.
     */
    logoutUrl: string | null;
  };
}

export const RUNTIME_CONFIG_URL = '/config.json';
