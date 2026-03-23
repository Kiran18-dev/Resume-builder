package com.example.resumebuilder.controller;

import com.example.resumebuilder.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    // Per user tracking
    private final Map<String, UserRateData> userRateMap = new ConcurrentHashMap<>();

    // Global counter
    private final AtomicInteger globalRequestCount = new AtomicInteger(0);
    private Instant globalWindowStart = Instant.now();

    // Config
    private static final int MAX_PER_USER_PER_HOUR = 3;
    private static final int MAX_GLOBAL_PER_HOUR = 20;
    private static final long HOUR_IN_SECONDS = 3600;

    // Track per user data
    static class UserRateData {
        int count = 0;
        Instant windowStart = Instant.now();

        boolean isAllowed() {
            // Reset window if 1 hour passed
            if (Instant.now().isAfter(windowStart.plusSeconds(HOUR_IN_SECONDS))) {
                count = 0;
                windowStart = Instant.now();
            }
            return count < MAX_PER_USER_PER_HOUR;
        }

        void increment() { count++; }

        long minutesLeft() {
            long secondsLeft = HOUR_IN_SECONDS -
                    (Instant.now().getEpochSecond() - windowStart.getEpochSecond());
            return Math.max(1, secondsLeft / 60);
        }
    }

    @GetMapping("/")
    public String home() { return "home"; }

    @GetMapping("/builder")
    public String builder() { return "index"; }

    @GetMapping("/about")
    public String about() { return "about"; }

    @GetMapping("/contact")
    public String contact() { return "contact"; }

    @GetMapping("/blog")
    public String blog() { return "blog"; }

    @GetMapping("/tips")
    public String tips() { return "tips"; }

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
            jakarta.servlet.http.HttpServletRequest request,
            Model model) {

        String ip = request.getRemoteAddr();
        System.out.println("Request from IP: " + ip);

        // ── Global rate limit check ──
        if (Instant.now().isAfter(globalWindowStart.plusSeconds(HOUR_IN_SECONDS))) {
            globalRequestCount.set(0);
            globalWindowStart = Instant.now();
            System.out.println("Global window reset");
        }

        if (globalRequestCount.get() >= MAX_GLOBAL_PER_HOUR) {
            System.out.println("GLOBAL LIMIT HIT: " + globalRequestCount.get());
            model.addAttribute("error",
                    "⚠️ Server is busy right now. Please try again in a few minutes!");
            return "index";
        }

        // ── Per user rate limit check ──
        UserRateData userData = userRateMap.computeIfAbsent(ip, k -> new UserRateData());

        if (!userData.isAllowed()) {
            long minsLeft = userData.minutesLeft();
            System.out.println("USER LIMIT HIT for IP: " + ip +
                    " | count: " + userData.count);
            model.addAttribute("error",
                    "⏳ You have used all 3 free generations this hour. " +
                            "Please wait " + minsLeft + " minute(s) to generate again!");
            return "index";
        }

        // ── Input validation ──
        if (name == null || name.trim().isEmpty()) {
            model.addAttribute("error", "Please enter your name!");
            return "index";
        }

        if (name.length() > 100 || email.length() > 100) {
            model.addAttribute("error", "Input too long! Please shorten your text.");
            return "index";
        }

        // Sanitize inputs
        name = name.replaceAll("<[^>]*>", "").trim();
        email = email.replaceAll("<[^>]*>", "").trim();
        phone = phone.replaceAll("<[^>]*>", "").trim();

        // ── Process photo ──
        String photoBase64 = "";
        if (photo != null && !photo.isEmpty()) {
            try {
                byte[] bytes = photo.getBytes();
                photoBase64 = "data:" + photo.getContentType() + ";base64," +
                        Base64.getEncoder().encodeToString(bytes);
            } catch (Exception e) {
                System.out.println("Photo processing failed: " + e.getMessage());
                photoBase64 = "";
            }
        }

        // ── Generate resume ──
        String resume = resumeService.generateResume(
                name, email, phone, location, linkedin, github,
                summary, skills, experience, education,
                certifications, languages, projects, template, photoBase64
        );

        // ── Increment counters only on success ──
        userData.increment();
        globalRequestCount.incrementAndGet();

        System.out.println("SUCCESS | IP: " + ip +
                " | User count: " + userData.count + "/" + MAX_PER_USER_PER_HOUR +
                " | Global: " + globalRequestCount.get() + "/" + MAX_GLOBAL_PER_HOUR);

        // Show remaining count to user
        int remaining = MAX_PER_USER_PER_HOUR - userData.count;
        if (remaining > 0) {
            model.addAttribute("info",
                    "✅ Resume generated! You have " + remaining +
                            " free generation(s) remaining this hour.");
        } else {
            model.addAttribute("info",
                    "✅ Resume generated! You have used all 3 free generations. " +
                            "Come back in 1 hour for more!");
        }

        model.addAttribute("resume", resume);
        model.addAttribute("template", template);
        return "index";
    }
}