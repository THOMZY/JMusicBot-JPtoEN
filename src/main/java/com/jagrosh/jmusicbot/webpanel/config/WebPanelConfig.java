/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.config;

import com.jagrosh.jmusicbot.Bot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Configuration
public class WebPanelConfig implements WebMvcConfigurer {

    private final Bot bot;

    public WebPanelConfig(Bot bot) {
        this.bot = bot;
    }

    @Bean
    public OncePerRequestFilter blockPropfindFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                if ("PROPFIND".equalsIgnoreCase(request.getMethod())) {
                    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return;
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
        
        // Add resource handler for local artwork
        // This assumes 'local_artwork' is a directory at the root of your application's runtime path
        String artworkDir = "file:local_artwork/";
        registry.addResourceHandler("/local_artwork/**")
                .addResourceLocations(artworkDir);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Development only - should be restricted in production
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
} 