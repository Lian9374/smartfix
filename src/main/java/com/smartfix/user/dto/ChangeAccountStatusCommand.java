package com.smartfix.user.dto;

import com.smartfix.user.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

/**
 * The "enable or disable this account" form.
 *
 * <p>Which accounts already exist is a server-side fact; this form only says what the
 * administrator wants the status to become.</p>
 */
public class ChangeAccountStatusCommand {

    @NotNull(message = "Account status is required.")
    private AccountStatus accountStatus;

    public ChangeAccountStatusCommand() {
        // form binding
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    @Override
    public String toString() {
        return "ChangeAccountStatusCommand{accountStatus=" + accountStatus + '}';
    }
}
