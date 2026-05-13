package com.rv.secureapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/secure/data")
    public String getSecureData() {
        return "Success! You have navigated mTLS and OAuth2 to access this data.";
    }
}