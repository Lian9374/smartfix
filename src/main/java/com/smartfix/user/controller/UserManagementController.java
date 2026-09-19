package com.smartfix.user.controller;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.common.exception.ResourceNotFoundException;
import com.smartfix.user.domain.AccountStatus;
import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.ChangeAccountStatusCommand;
import com.smartfix.user.dto.ChangeUserRoleCommand;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Administrator screens for accounts and roles ({@code /admin/users}, plan section 13.1).
 *
 * <p>Thin by design: it binds form input, delegates every decision to
 * {@link UserService}, and chooses a view. It holds no repository, no encoder and none
 * of the rules - the last-administrator rule in particular lives in the service, because
 * a rule enforced only here would disappear the moment anything else called the service.</p>
 *
 * <p>Successful writes redirect to {@code GET /admin/users} (POST-Redirect-GET), so
 * refreshing the result page does not repeat the action (AC28). Refused writes re-render
 * the list with the reason attached, so the administrator sees what happened on the page
 * they are already looking at.</p>
 *
 * <p><strong>Not yet protected.</strong> These routes must be restricted to
 * {@code ADMINISTRATOR}, but that is a URL rule in {@code SecurityConfig}, which belongs
 * to category B and is still the temporary permit-all baseline. Until B lands,
 * {@code /admin/**} is reachable by anyone who knows the URL.</p>
 */
@Controller
@RequestMapping("/admin/users")
public class UserManagementController {

    private static final String VIEW = "admin/users";

    private static final String CREATE_FORM = "createUserCommand";

    private final UserService userService;

    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    /** @return the account list, with the creation form collapsed */
    @GetMapping
    public String listUsers(Model model) {
        populateForListing(model);
        model.addAttribute("showCreateForm", false);
        return VIEW;
    }

    /** @return the account list with the creation form expanded */
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        populateForListing(model);
        registerEmptyCreateForm(model);
        model.addAttribute("showCreateForm", true);
        return VIEW;
    }

    /**
     * Creates an account.
     *
     * <p>400 when the submitted values are invalid, 409 when the username is already
     * taken. Both re-render the form so the administrator can correct it without
     * retyping everything. The password is never written back into the page.</p>
     */
    @PostMapping
    public ModelAndView createUser(@Valid @ModelAttribute(CREATE_FORM) CreateUserCommand command,
                                   BindingResult bindingResult,
                                   Model model,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderListing(model, HttpStatus.BAD_REQUEST, null);
        }
        try {
            userService.createUser(command, resolveActorUserId(principal));
        } catch (BusinessConflictException ex) {
            // A field-level error, because the username is the field to correct.
            bindingResult.rejectValue("username", "conflict", ex.getMessage());
            return renderListing(model, HttpStatus.CONFLICT, null);
        } catch (InputValidationException ex) {
            // Raised for a rule Bean Validation cannot express on its own - the
            // composition of the password, or a username that is too short only after
            // trimming. Reported against the form, never echoing the submitted value.
            return renderListing(model, HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Account '" + command.getUsername() + "' was created.");
        return new ModelAndView("redirect:/admin/users");
    }

    /** Changes an account's role. */
    @PostMapping("/{userId}/role")
    public ModelAndView changeRole(@PathVariable Long userId,
                                   @Valid @ModelAttribute ChangeUserRoleCommand command,
                                   BindingResult bindingResult,
                                   Model model,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderListing(model, HttpStatus.BAD_REQUEST, "Select a role before submitting.");
        }
        try {
            userService.changeRole(userId, command, resolveActorUserId(principal));
        } catch (BusinessConflictException ex) {
            return renderListing(model, HttpStatus.CONFLICT, ex.getMessage());
        }
        redirectAttributes.addFlashAttribute("successMessage", "The role was updated.");
        return new ModelAndView("redirect:/admin/users");
    }

    /**
     * Enables or disables an account.
     *
     * <p>An unknown {@code userId} is left to propagate as
     * {@link ResourceNotFoundException} so that category B's handler can turn it into
     * the 404 the route table specifies.</p>
     */
    @PostMapping("/{userId}/status")
    public ModelAndView changeAccountStatus(@PathVariable Long userId,
                                            @Valid @ModelAttribute ChangeAccountStatusCommand command,
                                            BindingResult bindingResult,
                                            Model model,
                                            Principal principal,
                                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderListing(model, HttpStatus.BAD_REQUEST, "Select an account status before submitting.");
        }
        try {
            userService.changeAccountStatus(userId, command, resolveActorUserId(principal));
        } catch (BusinessConflictException ex) {
            return renderListing(model, HttpStatus.CONFLICT, ex.getMessage());
        }
        redirectAttributes.addFlashAttribute("successMessage", "The account status was updated.");
        return new ModelAndView("redirect:/admin/users");
    }

    private void populateForListing(Model model) {
        model.addAttribute("users", userService.listUsers());
        model.addAttribute("roles", Role.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
    }

    /**
     * Registers the creation form's target object and its {@code BindingResult}.
     *
     * <p>A {@code th:object} form needs both before it has ever been submitted: without
     * them, the template has no object to bind {@code th:field} to and no result to ask
     * for field errors, and rendering fails outright rather than showing an empty form.
     * Spring supplies both by itself after a POST; on a plain GET it does not.</p>
     */
    private void registerEmptyCreateForm(Model model) {
        CreateUserCommand command = new CreateUserCommand();
        model.addAttribute(CREATE_FORM, command);
        model.addAttribute(BindingResult.MODEL_KEY_PREFIX + CREATE_FORM,
                new BeanPropertyBindingResult(command, CREATE_FORM));
    }

    /**
     * Re-renders the list at the given status.
     *
     * <p>The creation form is expanded only when the request that failed was itself a
     * creation attempt - that is, when a {@link CreateUserCommand} was bound, which is
     * also the only case in which the template has an object to render the form
     * against. A refused role or status change therefore comes back as the list with the
     * reason attached, which is what the administrator was looking at anyway.</p>
     *
     * <p>Attributes are added to the injected {@code model} rather than to a fresh
     * {@code ModelAndView}: Spring merges the model it has already built - including the
     * submitted form object and its {@code BindingResult}, which is why field errors
     * survive - over the top of whatever is returned here.</p>
     *
     * @param formError a message for the forms that are not bound to an object, or
     *                  {@code null} when the errors are already attached to fields
     */
    private ModelAndView renderListing(Model model, HttpStatus status, String formError) {
        boolean creatingAccount = model.containsAttribute(CREATE_FORM);
        if (!creatingAccount) {
            registerEmptyCreateForm(model);
        }
        populateForListing(model);
        model.addAttribute("showCreateForm", creatingAccount);
        if (formError != null) {
            model.addAttribute("formError", formError);
        }
        ModelAndView modelAndView = new ModelAndView(VIEW);
        modelAndView.setStatus(status);
        return modelAndView;
    }

    /**
     * Identifies the administrator performing the action.
     *
     * <p>Taken from the authenticated principal - never from a form field or a query
     * parameter, because anything the browser sends can be forged.</p>
     *
     * <p>{@code Principal} is used rather than B's {@code SmartFixUserDetails} so that
     * this module compiles and behaves correctly before category B exists. Once B lands,
     * the principal name is still the username, so this lookup keeps working; reading the
     * id straight off {@code SmartFixUserDetails} would only save one query.</p>
     *
     * <p>Returns {@code null} when nobody is signed in, or when the principal does not
     * correspond to an account. That is not a fabricated id - it is the honest answer,
     * and it stays reachable only while the security rules are still the permit-all
     * baseline. Once B protects {@code /admin/**} it cannot be hit.</p>
     */
    private Long resolveActorUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return userService.findAuthenticationByUsername(principal.getName()).userId();
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }
}
