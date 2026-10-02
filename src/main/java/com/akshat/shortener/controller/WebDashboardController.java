package com.akshat.shortener.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ==============================================================================
 * Web Dashboard Controller (Portfolio Frontend)
 * ==============================================================================
 * 
 * Serves the interactive user dashboard for recruiters, users, and portfolio showcase.
 */
@Controller
public class WebDashboardController {

    @Value("${scalelink.base-url:http://localhost:8080}")
    private String baseUrl;

    @GetMapping({"/", "/dashboard"})
    public String index(Model model) {
        model.addAttribute("baseUrl", baseUrl);
        return "index";
    }
}
