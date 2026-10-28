package com.smartfix.communitybridge;

import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.common.exception.InputValidationException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/** Session-bound form keys survive failed or uncertain submissions. Browser CSRF remains mandatory. */
@ControllerAdvice(basePackages="com.smartfix.community.controller") @Profile("community-remote")
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class RemoteFormKeys {
    private record Form(Long actor,long created) implements java.io.Serializable {}
    @SuppressWarnings("unchecked") private static Map<UUID,Form> forms(HttpSession session){synchronized(session){var keys=(Map<UUID,Form>)session.getAttribute("remoteCommunityForms");if(keys==null){keys=new LinkedHashMap<>();session.setAttribute("remoteCommunityForms",keys);}return keys;}}
    @ModelAttribute("communityCommandKey") public String key(HttpServletRequest request){
        var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof SmartFixUserDetails actor))return null;
        if(request.getMethod().equals("POST"))return require(request,actor.getUserId()).toString();
        var forms=forms(request.getSession());synchronized(forms){long now=System.currentTimeMillis();forms.values().removeIf(f->now-f.created()>86400000);while(forms.size()>=500)forms.remove(forms.keySet().iterator().next());UUID key=UUID.randomUUID();forms.put(key,new Form(actor.getUserId(),now));return key.toString();}
    }
    static UUID require(HttpServletRequest request,Long actor){
        try{UUID key=UUID.fromString(request.getParameter("communityCommandKey"));var forms=forms(request.getSession());synchronized(forms){Form f=forms.get(key);if(f==null||!actor.equals(f.actor())||System.currentTimeMillis()-f.created()>86400000)throw new IllegalArgumentException();}return key;}
        catch(Exception e){throw new InputValidationException("Form expired; reload before submitting.");}
    }
    @ExceptionHandler(ResponseStatusException.class) ModelAndView unavailable(ResponseStatusException e){var view=new ModelAndView("error");view.setStatus(e.getStatusCode());view.addObject("status",e.getStatusCode().value());view.addObject("communityRetryMessage","Result unconfirmed. Return to the original form and retry; do not create a second submission.");return view;}
}
