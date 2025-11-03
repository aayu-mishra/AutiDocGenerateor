package com.boa.hackathon.autodocgen.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

@Service
public class OllamaService {

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3"; // or "llama3.2" if that's what you pulled

    public String queryOllama(String prompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            JSONObject request = new JSONObject();
            request.put("model", MODEL);
            request.put("prompt", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(OLLAMA_API_URL, entity, String.class);

            return response.getBody();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}

