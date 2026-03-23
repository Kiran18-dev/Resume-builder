package com.example.resumebuilder.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.http.*;
import java.net.URI;
import org.json.*;

@Service
public class ResumeService {

    @Value("${groq.api.key}")
    private String apiKey;

    // SVG Icons
    private static final String LINKEDIN_SVG = "<svg width='14' height='14' viewBox='0 0 24 24' fill='currentColor' xmlns='http://www.w3.org/2000/svg'><path d='M16 8a6 6 0 016 6v7h-4v-7a2 2 0 00-2-2 2 2 0 00-2 2v7h-4v-7a6 6 0 016-6z'/><rect x='2' y='9' width='4' height='12'/><circle cx='4' cy='4' r='2'/></svg>";
    private static final String GITHUB_SVG = "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22'/></svg>";

    public String generateResume(String name, String email, String phone,
                                 String location, String linkedin, String github,
                                 String summary, String skills, String experience,
                                 String education, String certifications,
                                 String languages, String projects,
                                 String template, String photoBase64) {
        try {
            String prompt = "You are a professional resume writer.\n" +
                    "Generate ONLY the text content for a resume. No HTML, no markdown, no backticks.\n" +
                    "Follow STRICT rules:\n" +
                    "1. Write a strong 2-3 line professional summary\n" +
                    "2. List skills as comma separated\n" +
                    "3. Format experience with bullet points starting with action verbs\n" +
                    "4. Keep it ATS-friendly and concise\n" +
                    "5. Return ONLY a JSON object with these exact keys:\n" +
                    "   summary, skills, experience, education, projects, certifications\n" +
                    "6. experience, education, projects must be plain text with newlines\n" +
                    "7. No arrays, no extra text, ONLY the JSON\n\n" +
                    "User details:\n" +
                    "Name: " + name + "\n" +
                    "Email: " + email + "\n" +
                    "Phone: " + phone + "\n" +
                    "Location: " + location + "\n" +
                    "LinkedIn: " + linkedin + "\n" +
                    "GitHub: " + github + "\n" +
                    "Summary hint: " + summary + "\n" +
                    "Skills: " + skills + "\n" +
                    "Experience: " + experience + "\n" +
                    "Education: " + education + "\n" +
                    "Certifications: " + certifications + "\n" +
                    "Languages: " + languages + "\n" +
                    "Projects: " + projects;

            System.out.println("=== RESUME GENERATION START ===");
            System.out.println("Name: " + name + " | Template: " + template);

            String body = new JSONObject()
                    .put("model", "llama-3.3-70b-versatile")
                    .put("max_tokens", 1500)
                    .put("messages", new JSONArray()
                            .put(new JSONObject()
                                    .put("role", "user")
                                    .put("content", prompt)))
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            System.out.println("Calling Groq API...");

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Groq Response Status: " + response.statusCode());
            System.out.println("Groq Response Body: " + response.body());

            JSONObject json = new JSONObject(response.body());

            if (json.has("choices")) {
                String content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                System.out.println("AI Raw Content: " + content);

                content = content.replaceAll("```json", "")
                        .replaceAll("```", "")
                        .trim();

                int start = content.indexOf("{");
                int end = content.lastIndexOf("}");
                if (start != -1 && end != -1) {
                    content = content.substring(start, end + 1);
                }

                System.out.println("Cleaned JSON: " + content);

                JSONObject aiData;
                try {
                    aiData = new JSONObject(content);
                    System.out.println("JSON parsed successfully!");
                } catch (Exception parseError) {
                    System.out.println("JSON parse failed: " + parseError.getMessage());
                    System.out.println("Using fallback with user input...");
                    aiData = new JSONObject();
                    aiData.put("summary", summary.isEmpty() ?
                            "Motivated professional with expertise in " + skills : summary);
                    aiData.put("skills", skills);
                    aiData.put("experience", experience);
                    aiData.put("education", education);
                    aiData.put("projects", projects);
                    aiData.put("certifications", certifications);
                }

                System.out.println("Building HTML template: " + template);
                String result = buildResumeHtml(name, email, phone, location,
                        linkedin, github, languages, template, photoBase64, aiData);
                System.out.println("=== RESUME GENERATION SUCCESS ===");
                return result;

            } else if (json.has("error")) {
                String errorMsg = json.getJSONObject("error").getString("message");
                System.out.println("Groq API Error: " + errorMsg);
                JSONObject fallback = new JSONObject();
                fallback.put("summary", summary.isEmpty() ?
                        "Motivated professional with expertise in " + skills : summary);
                fallback.put("skills", skills);
                fallback.put("experience", experience);
                fallback.put("education", education);
                fallback.put("projects", projects);
                fallback.put("certifications", certifications);
                System.out.println("Using fallback due to API error...");
                return buildResumeHtml(name, email, phone, location,
                        linkedin, github, languages, template, photoBase64, fallback);
            }

        } catch (Exception e) {
            System.out.println("=== RESUME GENERATION FAILED ===");
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            try {
                JSONObject fallback = new JSONObject();
                fallback.put("summary", summary.isEmpty() ?
                        "Motivated professional with expertise in " + skills : summary);
                fallback.put("skills", skills);
                fallback.put("experience", experience);
                fallback.put("education", education);
                fallback.put("projects", projects);
                fallback.put("certifications", certifications);
                System.out.println("Using fallback after exception...");
                return buildResumeHtml(name, email, phone, location,
                        linkedin, github, languages, template, photoBase64, fallback);
            } catch (Exception fallbackError) {
                System.out.println("Fallback also failed: " + fallbackError.getMessage());
            }
        }
        return buildResumeHtml(name, email, phone, location,
                linkedin, github, languages, template, photoBase64, new JSONObject());
    }

    private String buildResumeHtml(String name, String email, String phone,
                                   String location, String linkedin, String github,
                                   String languages, String template,
                                   String photoBase64, JSONObject data) {

        String aiSummary = data.optString("summary", "");
        String aiSkills = data.optString("skills", "");
        String aiExperience = data.optString("experience", "");
        String aiEducation = data.optString("education", "");
        String aiProjects = data.optString("projects", "");
        String aiCerts = data.optString("certifications", "");

        String photoHtml = "";
        if (!photoBase64.isEmpty()) {
            photoHtml = "<img src='" + photoBase64 + "' style='width:80px;height:80px;" +
                    "border-radius:50%;object-fit:cover;border:3px solid white;'/>";
        }

        switch (template) {
            case "modern": return buildModern(name, email, phone, location, linkedin,
                    github, languages, photoHtml, aiSummary, aiSkills, aiExperience,
                    aiEducation, aiProjects, aiCerts);
            case "classic": return buildClassic(name, email, phone, location, linkedin,
                    github, languages, photoHtml, aiSummary, aiSkills, aiExperience,
                    aiEducation, aiProjects, aiCerts);
            case "minimal": return buildMinimal(name, email, phone, location, linkedin,
                    github, languages, photoHtml, aiSummary, aiSkills, aiExperience,
                    aiEducation, aiProjects, aiCerts);
            case "creative": return buildCreative(name, email, phone, location, linkedin,
                    github, languages, photoHtml, aiSummary, aiSkills, aiExperience,
                    aiEducation, aiProjects, aiCerts);
            default: return buildModern(name, email, phone, location, linkedin,
                    github, languages, photoHtml, aiSummary, aiSkills, aiExperience,
                    aiEducation, aiProjects, aiCerts);
        }
    }

    // ─── MODERN TEMPLATE ───────────────────────────────────────────
    private String buildModern(String name, String email, String phone,
                               String location, String linkedin, String github, String languages,
                               String photo, String summary, String skills, String experience,
                               String education, String projects, String certs) {

        String skillBadges = buildSkillBadges(skills, "white", "#2563EB");

        return "<div style='font-family:Arial,sans-serif;max-width:900px;margin:0 auto;" +
                "display:flex;background:white;'>" +
                "<div style='width:280px;min-width:280px;background:#2563EB;color:white;" +
                "padding:32px 24px;align-self:stretch;'>" +
                "<div style='text-align:center;margin-bottom:24px;'>" +
                photo +
                "<h1 style='font-size:22px;font-weight:800;margin:12px 0 4px;" +
                "color:white;line-height:1.2;'>" + name + "</h1>" +
                "</div>" +
                "<div style='margin-bottom:20px;'>" +
                "<p style='font-size:11px;opacity:0.7;text-transform:uppercase;" +
                "letter-spacing:1px;margin-bottom:10px;font-weight:700;'>CONTACT</p>" +
                contactItem("📧", email) +
                contactItem("📱", phone) +
                contactItem("📍", location) +
                linkedinItem(linkedin, "white") +
                githubItem(github, "white") +
                "</div>" +
                "<div style='margin-bottom:20px;'>" +
                "<p style='font-size:11px;opacity:0.7;text-transform:uppercase;" +
                "letter-spacing:1px;margin-bottom:10px;font-weight:700;'>SKILLS</p>" +
                "<div style='display:flex;flex-wrap:wrap;gap:6px;'>" + skillBadges + "</div>" +
                "</div>" +
                (languages.isEmpty() ? "" :
                        "<div style='margin-bottom:20px;'>" +
                                "<p style='font-size:11px;opacity:0.7;text-transform:uppercase;" +
                                "letter-spacing:1px;margin-bottom:10px;font-weight:700;'>LANGUAGES</p>" +
                                "<p style='font-size:12px;line-height:1.8;'>" +
                                languages.replace(",", "<br/>") + "</p></div>") +
                (certs.isEmpty() ? "" :
                        "<div style='margin-bottom:20px;'>" +
                                "<p style='font-size:11px;opacity:0.7;text-transform:uppercase;" +
                                "letter-spacing:1px;margin-bottom:10px;font-weight:700;'>CERTIFICATIONS</p>" +
                                "<p style='font-size:12px;line-height:1.8;'>" +
                                certs.replace(",", "<br/>").replace("\n", "<br/>") + "</p></div>") +
                "</div>" +
                "<div style='flex:1;padding:32px 28px;background:white;'>" +
                sectionBlock("PROFESSIONAL SUMMARY",
                        "<p style='font-size:12px;line-height:1.7;color:#374151;'>" + summary + "</p>",
                        "#2563EB") +
                sectionBlock("WORK EXPERIENCE", formatBulletSection(experience), "#2563EB") +
                sectionBlock("EDUCATION", formatBulletSection(education), "#2563EB") +
                sectionBlock("PROJECTS", formatBulletSection(projects), "#2563EB") +
                "</div></div>";
    }

    // ─── CLASSIC TEMPLATE ──────────────────────────────────────────
    private String buildClassic(String name, String email, String phone,
                                String location, String linkedin, String github, String languages,
                                String photo, String summary, String skills, String experience,
                                String education, String projects, String certs) {

        return "<div style='font-family:\"Times New Roman\",serif;max-width:860px;" +
                "margin:0 auto;padding:40px;background:white;color:#1a1a1a;'>" +
                "<div style='text-align:center;border-bottom:2px solid #1a1a1a;" +
                "padding-bottom:16px;margin-bottom:20px;'>" +
                "<h1 style='font-size:28px;font-weight:900;letter-spacing:2px;margin:0 0 8px;'>" +
                name.toUpperCase() + "</h1>" +
                "<p style='font-size:12px;color:#444;display:flex;justify-content:center;" +
                "align-items:center;flex-wrap:wrap;gap:8px;'>" +
                email + " &nbsp;|&nbsp; " + phone + " &nbsp;|&nbsp; " + location +
                (linkedin.isEmpty() ? "" : " &nbsp;|&nbsp; " + linkedinInline(linkedin, "#444")) +
                (github.isEmpty() ? "" : " &nbsp;|&nbsp; " + githubInline(github, "#444")) +
                "</p></div>" +
                classicSection("PROFESSIONAL SUMMARY",
                        "<p style='font-size:12px;line-height:1.7;'>" + summary + "</p>") +
                classicSection("SKILLS",
                        "<p style='font-size:12px;line-height:1.8;'>" + skills + "</p>") +
                classicSection("WORK EXPERIENCE", formatBulletSection(experience)) +
                classicSection("EDUCATION", formatBulletSection(education)) +
                classicSection("PROJECTS", formatBulletSection(projects)) +
                (certs.isEmpty() ? "" : classicSection("CERTIFICATIONS",
                        "<p style='font-size:12px;line-height:1.8;'>" + certs + "</p>")) +
                (languages.isEmpty() ? "" : classicSection("LANGUAGES",
                        "<p style='font-size:12px;line-height:1.8;'>" + languages + "</p>")) +
                "</div>";
    }

    // ─── MINIMAL TEMPLATE ──────────────────────────────────────────
    private String buildMinimal(String name, String email, String phone,
                                String location, String linkedin, String github, String languages,
                                String photo, String summary, String skills, String experience,
                                String education, String projects, String certs) {

        String skillBadges = buildSkillBadges(skills, "#0D9488", "#e6faf8");

        return "<div style='font-family:Arial,sans-serif;max-width:900px;" +
                "margin:0 auto;display:flex;background:white;'>" +
                "<div style='width:260px;min-width:260px;padding:40px 24px;" +
                "background:#f8fafc;border-right:3px solid #0D9488;align-self:stretch;'>" +
                "<div style='margin-bottom:28px;'>" +
                photo +
                "<h1 style='font-size:20px;font-weight:800;color:#0f172a;" +
                "margin:12px 0 4px;'>" + name + "</h1>" +
                "<p style='font-size:12px;color:#0D9488;font-weight:600;'>Professional</p>" +
                "</div>" +
                "<div style='margin-bottom:20px;'>" +
                "<p style='font-size:10px;color:#0D9488;text-transform:uppercase;" +
                "letter-spacing:1.5px;font-weight:800;margin-bottom:10px;'>CONTACT</p>" +
                minimalContact("✉", email) +
                minimalContact("☎", phone) +
                minimalContact("⊙", location) +
                linkedinItem(linkedin, "#0D9488") +
                githubItem(github, "#0D9488") +
                "</div>" +
                "<div style='margin-bottom:20px;'>" +
                "<p style='font-size:10px;color:#0D9488;text-transform:uppercase;" +
                "letter-spacing:1.5px;font-weight:800;margin-bottom:10px;'>SKILLS</p>" +
                "<div style='display:flex;flex-wrap:wrap;gap:5px;'>" + skillBadges + "</div>" +
                "</div>" +
                (languages.isEmpty() ? "" :
                        "<div style='margin-bottom:20px;'>" +
                                "<p style='font-size:10px;color:#0D9488;text-transform:uppercase;" +
                                "letter-spacing:1.5px;font-weight:800;margin-bottom:8px;'>LANGUAGES</p>" +
                                "<p style='font-size:12px;color:#475569;line-height:1.8;'>" +
                                languages.replace(",", "<br/>") + "</p></div>") +
                "</div>" +
                "<div style='flex:1;padding:40px 32px;'>" +
                minimalSection("SUMMARY",
                        "<p style='font-size:12px;color:#475569;line-height:1.7;'>" + summary + "</p>") +
                minimalSection("EXPERIENCE", formatBulletSection(experience)) +
                minimalSection("EDUCATION", formatBulletSection(education)) +
                minimalSection("PROJECTS", formatBulletSection(projects)) +
                (certs.isEmpty() ? "" : minimalSection("CERTIFICATIONS",
                        "<p style='font-size:12px;color:#475569;line-height:1.8;'>" + certs + "</p>")) +
                "</div></div>";
    }

    // ─── CREATIVE TEMPLATE ─────────────────────────────────────────
    private String buildCreative(String name, String email, String phone,
                                 String location, String linkedin, String github, String languages,
                                 String photo, String summary, String skills, String experience,
                                 String education, String projects, String certs) {

        String[] skillArr = skills.split(",");
        StringBuilder skillBars = new StringBuilder();
        String[] colors = {"#7C3AED","#ec4899","#0ea5e9","#10b981","#f59e0b"};
        for (int i = 0; i < skillArr.length; i++) {
            String color = colors[i % colors.length];
            int width = 65 + (i % 4) * 8;
            skillBars.append("<div style='margin-bottom:8px;'>" +
                    "<div style='font-size:11px;color:#e2e8f0;margin-bottom:3px;font-weight:600;'>" +
                    skillArr[i].trim() + "</div>" +
                    "<div style='background:rgba(255,255,255,0.1);border-radius:100px;height:5px;'>" +
                    "<div style='background:" + color + ";width:" + width +
                    "%;height:5px;border-radius:100px;'></div></div></div>");
        }

        return "<div style='font-family:Arial,sans-serif;max-width:900px;" +
                "margin:0 auto;background:white;'>" +
                "<div style='background:linear-gradient(135deg,#1E1E2E,#2d2b55);" +
                "padding:36px 40px;display:flex;align-items:center;gap:24px;'>" +
                (photo.isEmpty() ?
                        "<div style='width:80px;height:80px;border-radius:50%;" +
                                "background:linear-gradient(135deg,#7C3AED,#ec4899);" +
                                "display:flex;align-items:center;justify-content:center;" +
                                "font-size:28px;color:white;font-weight:800;flex-shrink:0;'>" +
                                name.substring(0,1).toUpperCase() + "</div>" : photo) +
                "<div>" +
                "<h1 style='font-size:28px;font-weight:900;color:white;margin:0 0 6px;'>" +
                name + "</h1>" +
                "<p style='font-size:13px;color:#a78bfa;margin:0 0 10px;font-weight:600;'>" +
                "Software Professional</p>" +
                "<div style='display:flex;flex-wrap:wrap;gap:12px;align-items:center;'>" +
                creativeContact(email) + creativeContact(phone) + creativeContact(location) +
                linkedinCreative(linkedin) +
                githubCreative(github) +
                "</div></div></div>" +
                "<div style='display:flex;'>" +
                "<div style='width:260px;min-width:260px;background:#1E1E2E;" +
                "padding:28px 20px;align-self:stretch;'>" +
                "<p style='font-size:10px;color:#7C3AED;text-transform:uppercase;" +
                "letter-spacing:2px;font-weight:800;margin-bottom:14px;'>SKILLS</p>" +
                skillBars +
                (languages.isEmpty() ? "" :
                        "<div style='margin-top:24px;'>" +
                                "<p style='font-size:10px;color:#7C3AED;text-transform:uppercase;" +
                                "letter-spacing:2px;font-weight:800;margin-bottom:10px;'>LANGUAGES</p>" +
                                "<p style='font-size:12px;color:#e2e8f0;line-height:1.8;'>" +
                                languages.replace(",", "<br/>") + "</p></div>") +
                (certs.isEmpty() ? "" :
                        "<div style='margin-top:24px;'>" +
                                "<p style='font-size:10px;color:#7C3AED;text-transform:uppercase;" +
                                "letter-spacing:2px;font-weight:800;margin-bottom:10px;'>CERTIFICATIONS</p>" +
                                "<p style='font-size:12px;color:#e2e8f0;line-height:1.8;'>" +
                                certs.replace("\n", "<br/>") + "</p></div>") +
                "</div>" +
                "<div style='flex:1;padding:28px;background:#fafafa;'>" +
                creativeSection("SUMMARY",
                        "<p style='font-size:12px;color:#374151;line-height:1.7;'>" + summary + "</p>") +
                creativeSection("EXPERIENCE", formatBulletSection(experience)) +
                creativeSection("EDUCATION", formatBulletSection(education)) +
                creativeSection("PROJECTS", formatBulletSection(projects)) +
                "</div></div></div>";
    }

    // ─── SOCIAL LINK HELPERS ────────────────────────────────────────

    private String cleanUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        url = url.trim();
        if (!url.startsWith("http")) url = "https://" + url;
        return url;
    }

    // LinkedIn icon for sidebar (modern, minimal)
    private String linkedinItem(String url, String color) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:6px;margin:6px 0;" +
                "text-decoration:none;color:" + color + ";font-size:11px;opacity:0.9;'>" +
                "<svg width='14' height='14' viewBox='0 0 24 24' fill='" + color + "'>" +
                "<path d='M16 8a6 6 0 016 6v7h-4v-7a2 2 0 00-2-2 2 2 0 00-2 2v7h-4v-7a6 6 0 016-6z'/>" +
                "<rect x='2' y='9' width='4' height='12'/><circle cx='4' cy='4' r='2'/></svg>" +
                "LinkedIn</a>";
    }

    // GitHub icon for sidebar (modern, minimal)
    private String githubItem(String url, String color) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:6px;margin:6px 0;" +
                "text-decoration:none;color:" + color + ";font-size:11px;opacity:0.9;'>" +
                "<svg width='14' height='14' viewBox='0 0 24 24' fill='none' " +
                "stroke='" + color + "' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                "<path d='M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 " +
                "6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 " +
                "2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 " +
                "5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22'/></svg>" +
                "GitHub</a>";
    }

    // LinkedIn inline for classic template
    private String linkedinInline(String url, String color) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:4px;" +
                "text-decoration:none;color:" + color + ";font-size:12px;'>" +
                "<svg width='13' height='13' viewBox='0 0 24 24' fill='" + color + "'>" +
                "<path d='M16 8a6 6 0 016 6v7h-4v-7a2 2 0 00-2-2 2 2 0 00-2 2v7h-4v-7a6 6 0 016-6z'/>" +
                "<rect x='2' y='9' width='4' height='12'/><circle cx='4' cy='4' r='2'/></svg>" +
                "LinkedIn</a>";
    }

    // GitHub inline for classic template
    private String githubInline(String url, String color) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:4px;" +
                "text-decoration:none;color:" + color + ";font-size:12px;'>" +
                "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' " +
                "stroke='" + color + "' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                "<path d='M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 " +
                "6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 " +
                "2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 " +
                "5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22'/></svg>" +
                "GitHub</a>";
    }

    // LinkedIn badge for creative template header
    private String linkedinCreative(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:6px;font-size:11px;" +
                "color:#cbd5e1;background:rgba(255,255,255,0.08);padding:4px 12px;" +
                "border-radius:100px;text-decoration:none;'>" +
                "<svg width='13' height='13' viewBox='0 0 24 24' fill='#a78bfa'>" +
                "<path d='M16 8a6 6 0 016 6v7h-4v-7a2 2 0 00-2-2 2 2 0 00-2 2v7h-4v-7a6 6 0 016-6z'/>" +
                "<rect x='2' y='9' width='4' height='12'/><circle cx='4' cy='4' r='2'/></svg>" +
                "LinkedIn</a>";
    }

    // GitHub badge for creative template header
    private String githubCreative(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        return "<a href='" + cleanUrl(url) + "' target='_blank' " +
                "style='display:inline-flex;align-items:center;gap:6px;font-size:11px;" +
                "color:#cbd5e1;background:rgba(255,255,255,0.08);padding:4px 12px;" +
                "border-radius:100px;text-decoration:none;'>" +
                "<svg width='13' height='13' viewBox='0 0 24 24' fill='none' " +
                "stroke='#a78bfa' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'>" +
                "<path d='M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 00-.94-2.61c3.14-.35 " +
                "6.44-1.54 6.44-7A5.44 5.44 0 0020 4.77 5.07 5.07 0 0019.91 1S18.73.65 16 " +
                "2.48a13.38 13.38 0 00-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 005 4.77a5.44 " +
                "5.44 0 00-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 009 18.13V22'/></svg>" +
                "GitHub</a>";
    }

    // ─── CONTACT HELPERS ───────────────────────────────────────────
    private String contactItem(String icon, String text) {
        return "<p style='font-size:11px;margin:6px 0;opacity:0.9;word-break:break-all;'>" +
                icon + " " + text + "</p>";
    }

    private String minimalContact(String icon, String text) {
        return "<p style='font-size:11px;color:#475569;margin:5px 0;word-break:break-all;'>" +
                icon + " " + text + "</p>";
    }

    private String creativeContact(String text) {
        return "<span style='font-size:11px;color:#cbd5e1;background:rgba(255,255,255,0.08);" +
                "padding:3px 10px;border-radius:100px;'>" + text + "</span>";
    }

    private String buildSkillBadges(String skills, String color, String bg) {
        String[] arr = skills.split(",");
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (!s.trim().isEmpty()) {
                sb.append("<span style='background:").append(bg)
                        .append(";color:").append(color)
                        .append(";padding:3px 10px;border-radius:100px;font-size:11px;")
                        .append("font-weight:600;display:inline-block;margin:2px;'>")
                        .append(s.trim()).append("</span>");
            }
        }
        return sb.toString();
    }

    private String formatBulletSection(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        text = text.replaceAll("\\[\"", "").replaceAll("\"\\]", "")
                .replaceAll("\",\\s*\"", "\n").replaceAll("\\\\n", "\n")
                .replaceAll("\\*\\*", "").trim();
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (line.trim().startsWith("•") || line.trim().startsWith("-")
                    || line.trim().startsWith("*")) {
                sb.append("<p style='font-size:12px;margin:3px 0 3px 16px;" +
                                "line-height:1.6;color:#374151;'>• ")
                        .append(line.replaceFirst("^[•\\-\\*]\\s*", ""))
                        .append("</p>");
            } else {
                sb.append("<p style='font-size:13px;font-weight:700;margin:10px 0 4px;" +
                                "color:#0f172a;'>")
                        .append(line).append("</p>");
            }
        }
        return sb.toString();
    }

    private String sectionBlock(String title, String content, String color) {
        return "<div style='margin-bottom:20px;'>" +
                "<h2 style='font-size:13px;font-weight:800;color:" + color + ";" +
                "border-bottom:2px solid " + color + ";padding-bottom:5px;" +
                "margin-bottom:10px;letter-spacing:1px;'>" + title + "</h2>" +
                content + "</div>";
    }

    private String classicSection(String title, String content) {
        return "<div style='margin-bottom:16px;'>" +
                "<h2 style='font-size:13px;font-weight:900;border-bottom:1px solid #1a1a1a;" +
                "padding-bottom:4px;margin-bottom:8px;letter-spacing:1px;'>" + title + "</h2>" +
                content + "</div>";
    }

    private String minimalSection(String title, String content) {
        return "<div style='margin-bottom:22px;'>" +
                "<h2 style='font-size:11px;font-weight:800;color:#0D9488;" +
                "text-transform:uppercase;letter-spacing:2px;margin-bottom:10px;" +
                "padding-bottom:4px;border-bottom:1px solid #ccfbf1;'>" + title + "</h2>" +
                content + "</div>";
    }

    private String creativeSection(String title, String content) {
        return "<div style='margin-bottom:20px;'>" +
                "<h2 style='font-size:11px;font-weight:800;color:#7C3AED;" +
                "text-transform:uppercase;letter-spacing:2px;margin-bottom:10px;" +
                "padding-bottom:5px;border-bottom:2px solid #7C3AED;'>" + title + "</h2>" +
                content + "</div>";
    }
}