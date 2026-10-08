package com.smartfix.auth.controller;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.user.dto.RegistrationCommand;
import com.smartfix.user.service.UserService;
import com.smartfix.user.validation.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The public sign-up route: {@code GET /register} and {@code POST /register}.
 *
 * <p>It lives beside {@code LoginController} rather than with the account module's
 * administrator screens, because it is the front door's other half: the two pages lead to
 * each other, share a layout, and serve the same person - somebody who is not signed in.
 * What it shares with {@code UserManagementController} is the division of labour (bind,
 * delegate, choose a view) and the rule that the acting account never comes from the
 * form.</p>
 *
 * <h2>What this route may not do</h2>
 *
 * <p>An anonymous route that writes to the account table is the most sensitive thing in
 * the application, so the constraints are stated rather than implied:</p>
 *
 * <ul>
 *   <li>It creates exactly one kind of account. The role is not on the form; see
 *       {@link RegistrationCommand}.</li>
 *   <li>It never signs anybody in. A successful registration redirects to the sign-in
 *       page, so the new account proves its password before it is given a session - and a
 *       mistyped username is found now rather than from inside a session.</li>
 *   <li>A refusal re-renders the form with a field-level message and never with a stack
 *       trace, a SQL fragment or a constraint name.</li>
 * </ul>
 *
 * <p>CSRF applies as it does everywhere else. Category B's {@code SecurityConfig} permits
 * this one route anonymously - {@code GET} to render it, {@code POST} to submit it - and
 * nothing else.</p>
 */
@Controller
public class RegistrationController {

    static final String REGISTER_VIEW = "register";

    private static final String FORM = "registrationCommand";

    /**
     * Shown on the sign-in page after a successful registration.
     *
     * <p>A flash attribute and not a query parameter: it is a sentence about something that
     * just happened, it belongs to one redirect, and a message carried in the URL is a
     * message anybody could hand a reader.</p>
     */
    static final String CREATED_MESSAGE = "Account created. Sign in to continue.";

    private final UserService userService;

    public RegistrationController(UserService userService) {
        this.userService = userService;
    }

    /** @return the empty sign-up form, with the session its token needs already started */
    @GetMapping("/register")
    public String form(HttpServletRequest request, Model model) {
        CsrfTokens.resolve(request);
        registerEmptyForm(model);
        addPolicyHints(model);
        return REGISTER_VIEW;
    }

    /**
     * Creates the account and sends the visitor to the sign-in page.
     *
     * <p>POST-redirect-GET, so refreshing after signing up does not try to sign up again.
     * The new account is not signed in automatically: the redirect goes to the sign-in page,
     * where the Requester entry is already selected - which is what the visitor just
     * became.</p>
     *
     * <p>400 when the submitted values are invalid, 409 when the username is taken. Both
     * re-render the form with everything except the two secrets still in it, so a visitor
     * corrects one field instead of retyping the page.</p>
     */
    @PostMapping("/register")
    public ModelAndView register(@Valid @ModelAttribute(FORM) RegistrationCommand command,
                                 BindingResult errors,
                                 Model model,
                                 RedirectAttributes redirect) {
        if (!errors.hasErrors() && !command.passwordsMatch()) {
            // The confirmation is a rule about two fields at once, which no annotation on
            // either one can express, so it attaches here. The service applies it again;
            // see UserService.registerRequester.
            errors.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        }
        if (errors.hasErrors()) {
            return renderForm(model, HttpStatus.BAD_REQUEST);
        }
        try {
            userService.registerRequester(command);
        } catch (BusinessConflictException taken) {
            // Field-level, because the username is the field to change. The message comes
            // from the service and names no constraint.
            errors.rejectValue("username", "conflict", taken.getMessage());
            return renderForm(model, HttpStatus.CONFLICT);
        } catch (InputValidationException refused) {
            // A rule Bean Validation cannot express: a username that is too short only after
            // trimming, or the confirmation the service re-checked. Reported against the
            // form as a whole, never echoing the submitted value.
            errors.reject("registration.refused", refused.getMessage());
            return renderForm(model, HttpStatus.BAD_REQUEST);
        }
        redirect.addFlashAttribute("successMessage", CREATED_MESSAGE);
        return new ModelAndView("redirect:/login");
    }

    /**
     * Renders the form again, with the visitor's answers and the reason it was refused.
     *
     * <p>Both secrets are dropped first. The template keeps them out of the HTML by not
     * binding them, but that is a property of the markup; clearing them here makes it a
     * property of the data, so no later edit to the page can put a submitted password back
     * into a response.</p>
     */
    private ModelAndView renderForm(Model model, HttpStatus status) {
        if (model.getAttribute(FORM) instanceof RegistrationCommand command) {
            command.clearSecrets();
        }
        addPolicyHints(model);
        ModelAndView modelAndView = new ModelAndView(REGISTER_VIEW);
        modelAndView.setStatus(status);
        return modelAndView;
    }

    /**
     * Registers the form's target object and its binding result.
     *
     * <p>A {@code th:object} form needs both before it has ever been submitted: without
     * them the template has no object to bind {@code th:field} to and no result to ask for
     * field errors, and rendering fails outright rather than showing an empty form. Spring
     * supplies both by itself after a POST; on a plain GET it does not.</p>
     */
    private void registerEmptyForm(Model model) {
        RegistrationCommand command = new RegistrationCommand();
        model.addAttribute(FORM, command);
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + FORM,
                new BeanPropertyBindingResult(command, FORM));
    }

    /**
     * Passes the password rules to the page as numbers, so the hint under the field and the
     * rule the server applies are the same two values rather than two copies of them.
     */
    private void addPolicyHints(Model model) {
        model.addAttribute("passwordMinLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("passwordMaxBytes", PasswordPolicy.MAX_UTF8_BYTES);
    }
}
