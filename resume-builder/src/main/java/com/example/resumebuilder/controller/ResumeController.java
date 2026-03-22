package com.example.resumebuilder.controller;

import com.example.resumebuilder.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;

@Controller
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/generate")
    public String generateResume(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String location,
            @RequestParam String linkedin,
            @RequestParam String github,
            @RequestParam String summary,
            @RequestParam String skills,
            @RequestParam String experience,
            @RequestParam String education,
            @RequestParam String certifications,
            @RequestParam String languages,
            @RequestParam String projects,
            @RequestParam String template,
            @RequestParam(required = false) MultipartFile photo,
            Model model) {

        String photoBase64 = "";
        if (photo != null && !photo.isEmpty()) {
            try {
                byte[] bytes = photo.getBytes();
                photoBase64 = "data:" + photo.getContentType() + ";base64," +
                        Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                photoBase64 = "";
            }
        }

        String resume = resumeService.generateResume(
                name, email, phone, location, linkedin, github,
                summary, skills, experience, education,
                certifications, languages, projects, template, photoBase64
        );

        model.addAttribute("resume", resume);
        model.addAttribute("template", template);
        return "index";
    }
}