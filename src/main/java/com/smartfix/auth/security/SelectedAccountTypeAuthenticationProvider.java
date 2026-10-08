package com.smartfix.auth.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * The password check, and then the account-type check, in that order.
 *
 * <p>{@link DaoAuthenticationProvider} still does everything it did - it loads the
 * account, verifies the BCrypt hash, applies the policy around unknown users and disabled
 * accounts, and erases the credentials it no longer needs. This subclass adds one step
 * after all of that has already succeeded: if the visitor picked an account type and it is
 * not this account's, the sign-in is refused.</p>
 *
 * <h2>Why after the password rather than before</h2>
 *
 * <p>Checking the type first would answer "is this account a technician?" for anybody who
 * asked, without a password, turning the sign-in form into a role oracle. Checking it last
 * means the only person who learns anything is one who already knew the password.</p>
 *
 * <h2>Why here rather than on the page</h2>
 *
 * <p>A mismatch has to end the sign-in, not decorate it. Hiding the button, or redirecting
 * afterwards, would leave an authenticated session behind - the visitor would be signed in
 * as the account they said they were not. Throwing from the provider happens before
 * {@code AbstractAuthenticationProcessingFilter} has anywhere to put a successful
 * authentication, so the visit ends as a failed login: the context is cleared, no session
 * is marked authenticated, and nothing downstream has to remember to undo it.</p>
 *
 * <p>The message is not leaked either. Whatever the reason, the visitor is sent back to the
 * same page with an error; only the wording of that error differs, and it says that the
 * selected type did not match without saying what the account's type is. A visitor who has
 * not signed in must not be able to use this form to ask which accounts exist or what they
 * are, and this refusal deliberately answers neither.</p>
 */
public class SelectedAccountTypeAuthenticationProvider extends DaoAuthenticationProvider {

    public SelectedAccountTypeAuthenticationProvider(UserDetailsService userDetailsService) {
        super(userDetailsService);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication authenticated = super.authenticate(authentication);
        if (authentication.getDetails() instanceof SelectedAccountType selected
                && authenticated.getPrincipal() instanceof SmartFixUserDetails account
                && selected.contradicts(account.getRole())) {
            // The role is read from the principal, which was built from the persisted
            // account. Nothing from the request takes part in this decision except the
            // claim being refused.
            throw new SelectedAccountTypeMismatchException();
        }
        return authenticated;
    }

    /**
     * Deliberately the same token type the delegate supports: the wrapper must be
     * substitutable for it, or the {@code ProviderManager} would stop routing form logins
     * here.
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
