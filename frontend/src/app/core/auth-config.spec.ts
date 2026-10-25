import { toOidcConfig } from './auth-config';

describe('toOidcConfig', () => {
  const config = toOidcConfig(
    { oidc: { authority: 'https://cognito-idp.eu-west-3.amazonaws.com/pool', clientId: 'spa', scope: 'openid email', logoutUrl: null } },
    'https://shop.example',
  );

  it('uses the authorization code flow with PKCE and refresh tokens', () => {
    expect(config.responseType).toBe('code');
    expect(config.useRefreshToken).toBe(true);
    expect(config.redirectUrl).toBe('https://shop.example/auth/callback');
  });

  it('only attaches the access token to our API', () => {
    expect(config.secureRoutes).toEqual(['/api/']);
  });
});
