package com.smartfix.auth.security;

import org.springframework.security.authentication.AccountStatusException;

/**
 * The credentials were right and the selected account type was not.
 *
 * <h2>Why {@link AccountStatusException} and not {@code BadCredentialsException}</h2>
 *
 * <p>The natural parent looks like {@code BadCredentialsException} - as a pair, this
 * account and this selection are not valid credentials - but it does not work, and the
 * reason is worth writing down because nothing about the code says it.</p>
 *
 * <p>{@code ProviderManager} treats the two differently. A provider that throws a plain
 * {@code AuthenticationException} only records it as the last failure and then asks the
 * next provider; the form-login filter's manager is a local one whose parent is the
 * application-wide authentication manager, which Spring Boot builds from the
 * {@code SmartFixUserDetailsService} bean. That parent has no idea a selection was made,
 * so with a {@code BadCredentialsException} the parent authenticates the very same
 * credentials and the login <strong>succeeds</strong> - the refusal is swallowed and the
 * visitor ends up signed in as the account they said they were not.</p>
 *
 * <p>{@code AccountStatusException} is the framework's "this account may not sign in"
 * signal, and it is answered on the spot: {@code ProviderManager} rethrows it rather than
 * consulting any further provider. That is exactly the semantics this refusal needs, and
 * it is the same mechanism a disabled account already relies on - the subclass's own
 * {@code DisabledException} from {@code preAuthenticationChecks} is an
 * {@code AccountStatusException} too, so nothing here is a special case.</p>
 *
 * <p>The consequence is identical to a wrong password: no authentication is set, no
 * session is left authenticated, and the visitor is returned to the sign-in page.</p>
 *
 * <p>The message is for logs, not for the page. It says that the pair did not match and
 * deliberately does not name the type the account actually has, which is the one fact this
 * refusal could leak.</p>
 */
public class SelectedAccountTypeMismatchException extends AccountStatusException {

    private static final long serialVersionUID = 1L;

    public SelectedAccountTypeMismatchException() {
        super("The selected account type does not match the account.");
    }
}
