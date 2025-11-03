package com.boa.hackathon.autodocgen.service;

import com.boa.hackathon.autodocgen.model.ClassMetadata;
import com.boa.hackathon.autodocgen.model.MethodMeta;
import com.boa.hackathon.autodocgen.util.EnglishWordList;

import java.util.*;
import java.util.stream.Collectors;

public class DocQualityAnalyzer {

    /**
     * Find duplicate lines (exact) and duplicate blocks (n-gram) inside a documentation String.
     */
    public static Map<String, Object> analyzeDocText(String docText) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> lines = Arrays.stream(docText.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // exact duplicate lines
        Map<String, Integer> lineCounts = new LinkedHashMap<>();
        for (String l : lines) lineCounts.put(l, lineCounts.getOrDefault(l,0)+1);
        Map<String,Integer> dupLines = lineCounts.entrySet().stream()
                .filter(e->e.getValue()>1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put("duplicateLines", dupLines);

        // n-gram duplicate blocks (3-line blocks)
        Map<String,Integer> blockCounts = new LinkedHashMap<>();
        int N=3;
        for (int i=0;i+N<=lines.size();i++){
            String block = String.join(" ", lines.subList(i,i+N));
            blockCounts.put(block, blockCounts.getOrDefault(block,0)+1);
        }
        Map<String,Integer> dupBlocks = blockCounts.entrySet().stream()
                .filter(e->e.getValue()>1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put("duplicateBlocks", dupBlocks);

        // simple spelling heuristics
        Map<String,Integer> suspectWords = new LinkedHashMap<>();
        for (String l : lines) {
            for (String w : l.split("\\s+")) {
                String clean = w.replaceAll("[^A-Za-z']", "").toLowerCase();
                if (clean.length() > 2 && !EnglishWordList.isCommonWord(clean)) {
                    suspectWords.put(clean, suspectWords.getOrDefault(clean,0)+1);
                }
            }
        }
        // top suspect words (those seen >1)
        Map<String,Integer> topSuspects = suspectWords.entrySet().stream()
                .filter(e->e.getValue()>1)
                .limit(200)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put("suspectWords", topSuspects);

        return result;
    }

    /**
     * Compute method-level similarity across all classes to suggest reuse.
     * Returns pairs of (methodA, methodB, score) where score in [0,1].
     */
    public static List<Map<String,Object>> detectSimilarMethods(List<ClassMetadata> classes) {
        List<Map<String,Object>> result = new ArrayList<>();
        // flatten methods with context
        List<MethodContext> methods = new ArrayList<>();
        for (ClassMetadata c : classes) {
            if (c.getMethods()==null) continue;
            for (MethodMeta m : c.getMethods()) {
                methods.add(new MethodContext(c.getClassName(), c.getPackageName(), m));
            }
        }
        for (int i=0;i<methods.size();i++){
            for (int j=i+1;j<methods.size();j++){
                MethodContext a = methods.get(i), b = methods.get(j);
                double score = similarityScore(a.method, b.method);
                if (score >= 0.6) {
                    Map<String,Object> pair = new LinkedHashMap<>();
                    pair.put("classA", a.className);
                    pair.put("methodA", a.method.getName());
                    pair.put("classB", b.className);
                    pair.put("methodB", b.method.getName());
                    pair.put("score", Math.round(score*100.0)/100.0);
                    // quick hint: if names similar -> reuse suggestion
                    pair.put("suggestion", score>0.8 ? "Highly similar — consider refactor/reuse" : "Possible reuse");
                    result.add(pair);
                }
            }
        }
        // sort by score desc
        result.sort((x,y)->Double.compare((double)y.get("score"), (double)x.get("score")));
        return result;
    }

    /**
     * Detect duplicate method bodies (exact normalized match).
     * This requires reading filePath from extra map inside ClassMetadata (if available)
     */
    public static List<Map<String,Object>> detectDuplicateMethodBodies(List<ClassMetadata> classes) {
        Map<String, List<MethodBodyEntry>> bodyMap = new HashMap<>();
        List<Map<String,Object>> duplicates = new ArrayList<>();
        for (ClassMetadata c : classes) {
            if (c.getMethods()==null) continue;
            for (MethodMeta m: c.getMethods()) {
                String body = m.getBody(); // if parser set it
                if (body==null || body.isBlank()) continue;
                String norm = normalizeCode(body);
                bodyMap.computeIfAbsent(norm, k->new ArrayList<>())
                        .add(new MethodBodyEntry(c.getClassName(), m.getName()));
            }
        }
        for (Map.Entry<String,List<MethodBodyEntry>> e : bodyMap.entrySet()) {
            List<MethodBodyEntry> list = e.getValue();
            if (list.size()>1) {
                Map<String,Object> rec = new LinkedHashMap<>();
                rec.put("methods", list);
                rec.put("occurrences", list.size());
                duplicates.add(rec);
            }
        }
        return duplicates;
    }

    private static String normalizeCode(String s) {
        return s.replaceAll("\\s+"," ").replaceAll("\\b\\w{1}\\b","").trim();
    }

    private static double similarityScore(MethodMeta a, MethodMeta b) {
        // tokens from name, params, repo calls, domain keywords
        Set<String> ta = tokensForMethod(a);
        Set<String> tb = tokensForMethod(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta); inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta); union.addAll(tb);
        double j = (double)inter.size() / (double)union.size();
        return j;
    }

    private static Set<String> tokensForMethod(MethodMeta m) {
        Set<String> tokens = new HashSet<>();
        if (m.getName()!=null) {
            for (String p : splitCamel(m.getName())) tokens.add(p.toLowerCase());
        }
        if (m.getParams()!=null) {
            for (String p : m.getParams()) {
                String[] parts = p.split("[^A-Za-z]+");
                for (String q: parts) if (!q.isBlank()) tokens.add(q.toLowerCase());
            }
        }
        if (m.getRepositoryCalls()!=null) m.getRepositoryCalls().forEach(s->tokens.add(s.toLowerCase()));
        if (m.getDomainKeywords()!=null) m.getDomainKeywords().forEach(s->tokens.add(s.toLowerCase()));
        return tokens;
    }

    private static List<String> splitCamel(String s) {
        return Arrays.stream(s.split("(?=[A-Z])|[_\\-]")).filter(x->!x.isBlank()).collect(Collectors.toList());
    }

    // helper classes
    private static class MethodContext {
        String className, pkg;
        MethodMeta method;
        MethodContext(String className, String pkg, MethodMeta method){ this.className=className; this.pkg=pkg; this.method=method; }
    }
    private static class MethodBodyEntry { public String className, methodName; public MethodBodyEntry(String c,String m){this.className=c;this.methodName=m;} }
}
