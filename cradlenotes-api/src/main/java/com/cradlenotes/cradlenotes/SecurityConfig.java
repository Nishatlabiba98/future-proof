package com.cradlenotes.cradlenotes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * SecurityConfig - multi-user authentication for CradleNotes
 *
 * To change a password:
 *   1. Find the user below
 *   2. Update the encode("...") value
 *   3. Restart the server
 *
 * To add a new user:
 *   1. Copy any UserDetails block below
 *   2. Change username and password
 *   3. Add the new variable to InMemoryUserDetailsManager(...)
 *   4. Restart the server
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/login", "/logout").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login.html?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login.html")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {

        // ---- EDIT PASSWORDS HERE ----
        // Replace CHANGE_ME with each person's real password

        UserDetails nishat = User.builder()
                .username("nishat")
                .password(passwordEncoder().encode("Kervon@98"))
                .roles("USER")
                .build();

        UserDetails josh = User.builder()
                .username("josh")
                .password(passwordEncoder().encode("Kervon@98"))
                .roles("USER")
                .build();

        UserDetails alani = User.builder()
                .username("alani")
                .password(passwordEncoder().encode("Kervon@98"))
                .roles("USER")
                .build();

        UserDetails nishan = User.builder()
                .username("nishan")
                .password(passwordEncoder().encode("Kervon@98"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(nishat, josh, alani, nishan);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
