package co.edu.escuelaing.secure_web_system_as_an_enterprise_risk_control;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/api/risk-status").hasAuthority("SCOPE_read:risk")
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .build();
  }
  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.allowed-origins}") List<String> origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
