export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api',  // Auth Service
  keycloak: {
    issuer: 'http://localhost:8180/realms/visioconference-realm',
    clientId: 'visioconference-frontend',
    redirectUri: 'http://localhost:4200/auth/callback'
  }
};
