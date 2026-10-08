package com.smartfix.auth.controller;

import com.smartfix.auth.security.SelectedAccountType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Spring Security owns POST /login and POST /logout. */
@Controller
public class LoginController {

    /** The view; also the page a refused sign-in is redirected back to, with a reason. */
    static final String LOGIN_VIEW = "login";

    /**
     * Renders the sign-in form.
     *
     * <p>The token is resolved before the view is written — see {@link CsrfTokens} for why
     * that is load-bearing on this particular page.</p>
     *
     * <p>The name of the account-type field comes from {@link SelectedAccountType} rather
     * than from the page, so the field the browser submits and the field the provider reads
     * cannot drift apart. The three <em>values</em> stay written out in the markup, because
     * each one carries its own explanation and a loop over the enum would have to switch on
     * the name to say anything; {@code AuthenticationFlowIT} pins them to
     * {@code Role.values()} instead, which catches the failure that matters - a value no
     * role accepts.</p>
     */
    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        CsrfTokens.resolve(request);
        model.addAttribute("accountTypeField", SelectedAccountType.FORM_FIELD);
        // Which segment is checked, decided here rather than by three conditions in the
        // markup: exactly one option is always checked, and the rule that picks it is a
        // single line that can be tested on its own.
        //
        // The value comes back from a refusal (the failure handler puts the selected name
        // in the redirect) so the visitor's choice survives the round trip, and it falls
        // back to the requester for a page opened with no parameter at all, for a value
        // that names no account type, and for a hand-written URL. It decides nothing but
        // which segment starts out filled: the role a session ends up with comes from the
        // account row, and an account used through the wrong segment is refused outright.
        model.addAttribute("selectedAccountType", SelectedAccountType.defaultedName(request));
        return LOGIN_VIEW;
    }
}
