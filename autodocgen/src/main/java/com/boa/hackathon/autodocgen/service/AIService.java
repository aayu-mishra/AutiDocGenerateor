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

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3";

    public void enrichProject(ProjectMetadata pm) {
        pm.getClasses().forEach(this::enrichClass);
    }

    private void enrichClass(ClassMetadata cm) {
        try {
            String prompt = buildClassPrompt(cm);
            String ai = callOllama(prompt);
            cm.setAiDescription(ai);

            if (cm.getMethods() != null) {
                for (MethodMeta mm : cm.getMethods()) {
                    String mp = buildMethodPrompt(cm, mm);
                    String ma = callOllama(mp);
                    mm.setAiDescription(ma);
                }
            }
        } catch (Exception e) {
            log.warn("AI enrich failed for {}: {}", cm.getClassName(), e.getMessage());
        }
    }

    private String buildClassPrompt(ClassMetadata cm) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software engineer. Given the following class metadata, ")
                .append("write a detailed plain-English description of the class’s business responsibility and key points.\n")
                .append("ClassName: ").append(cm.getClassName()).append("\n")
                .append("Type: ").append(cm.getType()).append("\n")
                .append("Package: ").append(cm.getPackageName()).append("\n")
                .append("Fields: ").append(Optional.ofNullable(cm.getFields()).orElse(Collections.emptyList())).append("\n")
                .append("Methods: ");
        if (cm.getMethods() != null) {
            sb.append(cm.getMethods().stream().map(MethodMeta::getName).collect(Collectors.toList()));
        } else sb.append("[]");
        sb.append("\nDomain hints (from code): ");
        Set<String> domain = new HashSet<>();
        if (cm.getMethods() != null) {
            cm.getMethods().forEach(m -> {
                if (m.getDomainKeywords() != null) domain.addAll(m.getDomainKeywords());
            });
        }
        log.info("sb is "+ sb.toString());
        sb.append(domain).append("\n")
                .append("Write the answer as:\nDESCRIPTION: <long description>\nKEY_POINTS:\n - point1\n - point2");
        return sb.toString();
    }

    private String buildMethodPrompt(ClassMetadata cm, MethodMeta mm) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a software engineer. Explain this method in one sentence in business terms, and list any side-effects (DB writes, external calls) if any.\n")
                .append("Class: ").append(cm.getClassName()).append("\n")
                .append("Method: ").append(mm.getName()).append("\n")
                .append("Parameters: ").append(Optional.ofNullable(mm.getParams()).orElse(Collections.emptyList())).append("\n")
                .append("Repository calls: ").append(Optional.ofNullable(mm.getRepositoryCalls()).orElse(Collections.emptyList())).append("\n")
                .append("Domain keywords: ").append(Optional.ofNullable(mm.getDomainKeywords()).orElse(Collections.emptyList())).append("\n")
                .append("Answer format:\nSENTENCE: <one-liner>\nSIDE_EFFECTS: <list>");
        return sb.toString();
    }

    private String callOllama(String prompt) {
        try {
            log.info("calling ollama");
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "prompt", prompt
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.postForEntity(OLLAMA_URL, req, String.class);

            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
                return "No response from Ollama";
            }

            StringBuilder output = new StringBuilder();
            for (String line : resp.getBody().split("\n")) {
                if (line.trim().startsWith("{") && line.contains("\"response\"")) {
                    int start = line.indexOf("\"response\":\"");
                    if (start != -1) {
                        start += 12;
                        int end = line.indexOf("\"", start);
                        if (end > start) {
                            output.append(line, start, end);
                        }
                    }
                }
            }
            log.info("finished calling ollama " +output.toString());
            return output.toString().isBlank() ? resp.getBody() : output.toString();
        } catch (Exception e) {
            return "Error calling Ollama: " + e.getMessage();
        }
    }
}
