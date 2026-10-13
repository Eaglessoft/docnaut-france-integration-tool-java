package com.docnaut.clienttestportal.web;

import com.docnaut.clienttestportal.security.ServiceClientAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes only non-secret session identity to all server-rendered pages. */
@ControllerAdvice
public class SessionViewAdvice {
    @ModelAttribute
    public void sessionIdentity(Authentication authentication, Model model) {
        if (authentication instanceof ServiceClientAuthentication client) {
            model.addAttribute("selectedEnvironment", client.getEnvironment().name());
            model.addAttribute("signedInClientId", client.clientId());
        }
    }
}
