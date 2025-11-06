package com.boa.hackathon.autodocgen.service;

import com.boa.hackathon.autodocgen.model.ClassMetadata;
import com.boa.hackathon.autodocgen.model.MethodMeta;
import com.boa.hackathon.autodocgen.model.ProjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private final RestTemplate rest = new RestTemplate();

    private static final String OLLAMA_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "meta-llama/llama-3.3-8b-instruct:free";
    private static final String OPENROUTER_KEY = "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";

    public void enrichProject(ProjectMetadata pm) {
        pm.getClasses().forEach(this::enrichClass);
    }

    private void enrichClass(ClassMetadata cm) {
        try {
            String prompt = buildClassPrompt(cm);
            String ai = callOpenRouter(prompt);
            cm.setAiDescription(ai);

            if (cm.getMethods() != null) {
                for (MethodMeta mm : cm.getMethods()) {
                    String mp = buildMethodPrompt(cm, mm);
                    String ma = callOpenRouter(mp);
                    mm.setAiDescription(ma);
                }
            }
        } catch (Exception e) {
            log.warn("AI enrich failed for {}: {}", cm.getClassName(), e.getMessage());
        }
    }

    private String buildClassPrompt(ClassMetadata cm) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior backend engineer. Analyze the following Java class and explain its business purpose and behavior clearly.\n")
                .append("ClassName: ").append(cm.getClassName()).append("\n")
                .append("Type: ").append(cm.getType()).append("\n")
                .append("Package: ").append(cm.getPackageName()).append("\n")
                .append("Fields: ").append(Optional.ofNullable(cm.getFields()).orElse(Collections.emptyList())).append("\n")
                .append("Methods: ");
        if (cm.getMethods() != null) {
            sb.append(cm.getMethods().stream().map(MethodMeta::getName).collect(Collectors.toList()));
        } else sb.append("[]");
        sb.append("\nDomain hints: ");
        Set<String> domain = new HashSet<>();
        if (cm.getMethods() != null) {
            cm.getMethods().forEach(m -> {
                if (m.getDomainKeywords() != null) domain.addAll(m.getDomainKeywords());
            });
        }
        sb.append(domain).append("\n")
                .append("Write the answer as:\nDESCRIPTION: <explanation>\nKEY_POINTS:\n - point1\n - point2");
        return sb.toString();
    }

    private String buildMethodPrompt(ClassMetadata cm, MethodMeta mm) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a software engineer. Explain this Java method in one business-focused sentence and mention any side effects (like DB operations, service calls, etc.) if applicable.\n")
                .append("Class: ").append(cm.getClassName()).append("\n")
                .append("Method: ").append(mm.getName()).append("\n")
                .append("Parameters: ").append(Optional.ofNullable(mm.getParams()).orElse(Collections.emptyList())).append("\n")
                .append("Repository calls: ").append(Optional.ofNullable(mm.getRepositoryCalls()).orElse(Collections.emptyList())).append("\n")
                .append("Domain keywords: ").append(Optional.ofNullable(mm.getDomainKeywords()).orElse(Collections.emptyList())).append("\n")
                .append("Answer format:\nSENTENCE: <one-liner>\nSIDE_EFFECTS: <list>");
        return sb.toString();
    }

    private String callOpenRouter(String prompt) {
        try {
            if (OPENROUTER_KEY == null || OPENROUTER_KEY.isBlank()) {
                return "Error: OPENROUTER_API_KEY not set in environment variables.";
            }

            log.info("Calling OpenRouter API...");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", 100000);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + OPENROUTER_KEY);
            headers.set("HTTP-Referer", "https://autodocgen");
            headers.set("X-Title", "AutoDocGenerator");

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            log.info("request is "+ req);
            ResponseEntity<Map> resp = rest.exchange(OLLAMA_URL, HttpMethod.POST, req, Map.class);

            if (resp.getBody() == null) return "No response received";

            List<?> choices = (List<?>) resp.getBody().get("choices");
            if (choices == null || choices.isEmpty()) return "No choices returned";

            Map<?, ?> first = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) first.get("message");
            String content = (String) message.get("content");

            log.info("AI Response: {}", content);
            return content;
        } catch (Exception e) {
            log.error("Error calling OpenRouter: {}", e.getMessage());
            return "Error calling OpenRouter: " + e.getMessage();
        }
    }
}
