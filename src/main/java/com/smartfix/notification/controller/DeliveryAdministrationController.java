package com.smartfix.notification.controller;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.notification.service.*;
import com.smartfix.user.service.NotificationContactService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/admin/delivery")
public class DeliveryAdministrationController {
    @org.springframework.beans.factory.annotation.Value("${smartfix.cloud.enabled:false}")
    private boolean distributed;
    private final DeliveryAdministrationService service;private final DeliveryDispatcher dispatcher;private final NotificationContactService contacts;
    private final com.smartfix.notification.config.DeliveryProperties properties;
    public DeliveryAdministrationController(DeliveryAdministrationService service,DeliveryDispatcher dispatcher,NotificationContactService contacts,com.smartfix.notification.config.DeliveryProperties properties){this.service=service;this.dispatcher=dispatcher;this.contacts=contacts;this.properties=properties;}
    @GetMapping public String list(@AuthenticationPrincipal SmartFixUserDetails actor,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int contactPage,Model model){model.addAttribute("tasks",service.list(actor.getUserId(),page));model.addAttribute("contacts",contacts.list(actor.getUserId(),contactPage));model.addAttribute("emailEnabled",properties.emailEnabled);model.addAttribute("distributedDelivery",distributed);return "notification/delivery-admin";}
    @PostMapping("/{id}/retry") public String retry(@AuthenticationPrincipal SmartFixUserDetails actor,@PathVariable Long id,@RequestParam(defaultValue="0") int page,RedirectAttributes feedback){service.retry(actor.getUserId(),id);dispatcher.drain();feedback.addFlashAttribute("successMessage","Retry requested. Check the task status for the delivery result.");return "redirect:/admin/delivery?page="+Math.max(0,page);}
    @PostMapping("/contacts/{id}") public String contact(@AuthenticationPrincipal SmartFixUserDetails actor,@PathVariable Long id,@RequestParam(defaultValue="") String email,RedirectAttributes feedback){contacts.update(actor.getUserId(),id,email);feedback.addFlashAttribute("successMessage","Business email contact updated.");return "redirect:/admin/delivery";}
}
