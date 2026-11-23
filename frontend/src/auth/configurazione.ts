import { InMemoryWebStorage, WebStorageStateStore } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'

/**
 * Login con Keycloak (OpenID Connect, Authorization Code + PKCE).
 *
 * I token restano solo in memoria, mai in localStorage o sessionStorage:
 * uno script malevolo nella pagina non li troverebbe salvati. Se si ricarica
 * la pagina, Keycloak riconosce la sessione e rilascia un nuovo token senza
 * chiedere di nuovo la password.
 */
export const configurazioneOidc: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/nis2',
  client_id: 'nis2-frontend',
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  scope: 'openid profile',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  automaticSilentRenew: true,
  // Dopo il login toglie dall'indirizzo i parametri ?code=...&state=...
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
