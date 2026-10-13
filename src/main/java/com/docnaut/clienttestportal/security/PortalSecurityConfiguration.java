package com.docnaut.clienttestportal.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class PortalSecurityConfiguration {

    /**
     * Credential erasure is disabled because the session must keep the client secret to renew its
     * access token. The secret never leaves the server-side session.
     */
    @Bean
    AuthenticationManager authenticationManager(ServiceClientAuthenticationProvider provider) {
        ProviderManager manager = new ProviderManager(List.of(provider));
        manager.setEraseCredentialsAfterAuthentication(false);
        return manager;
    }

    @Bean
    SecurityFilterChain portalChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/assets/**", "/actuator/health/**").permitAll()
                        .anyRequest().hasAuthority(ServiceClientAuthentication.AUTHORITY))
                .formLogin(login -> login.loginPage("/login")
                        .authenticationDetailsSource(request -> request.getParameter("environment"))
                        .usernameParameter("clientId")
                        .passwordParameter("clientSecret")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?failed"))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?signedOut")
                        .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .headers(headers -> headers.cacheControl(cache -> {}));
        return http.build();
    }
}
