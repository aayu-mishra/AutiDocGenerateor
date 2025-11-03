package com.boa.hackathon.autodocgen.service;
import com.boa.hackathon.autodocgen.model.ClassMetadata;
import com.boa.hackathon.autodocgen.model.MethodMeta;
import com.boa.hackathon.autodocgen.model.ProjectMetadata;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import java.util.regex.Pattern;

@Service
public class RepoParserService {


    private static final Logger log = LoggerFactory.getLogger(RepoParserService.class);
    private static final Path BASE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "autodoc_repos");
    private static final Pattern SAFE = Pattern.compile("[^A-Za-z0-9._-]");

    static {
        ParserConfiguration config = new ParserConfiguration();
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        StaticJavaParser.setConfiguration(config);
    }

    public ProjectMetadata cloneAndParse(String urlRaw) throws Exception {
        String url = sanitize(urlRaw);
        log.info("Sanitized URL: {}", url);
        if (!(url.startsWith("https://github.com/") || url.startsWith("git@github.com:"))) {
            throw new IllegalArgumentException("Only GitHub URLs supported");
        }
        String repoName = extractRepoName(url);
        String safeName = SAFE.matcher(repoName).replaceAll("_");
        Path repoDir = BASE_DIR.resolve(safeName);
        Files.createDirectories(BASE_DIR);
        if (Files.exists(repoDir)) deleteRecursively(repoDir);
        log.info("Cloning {} -> {}", url, repoDir);
        Git.cloneRepository().setURI(url).setDirectory(repoDir.toFile()).call();

        ProjectMetadata pm = new ProjectMetadata();
        pm.setProjectName(repoName);
        List<ClassMetadata> classes = new ArrayList<>();
        // parse java files
        Files.walk(repoDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(p);
                        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                            ClassMetadata cm = new ClassMetadata();
                            cm.setClassName(c.getNameAsString());
                            cm.setPackageName(cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse(""));
                            cm.setType(detectType(c));
                            cm.setComment(c.getComment().map(Comment::getContent).orElse("No comment"));

                            List<String> fields = new ArrayList<>();
                            c.getFields().forEach(fd -> fields.add(fd.getVariables().toString()));
                            cm.setFields(fields);

                            List<MethodMeta> methods = new ArrayList<>();
                            c.getMethods().forEach(m -> {
                                MethodMeta mm = new MethodMeta();
                                mm.setName(m.getNameAsString());
                                List<String> params = new ArrayList<>();
                                m.getParameters().forEach(pv -> params.add(pv.getTypeAsString() + " " + pv.getNameAsString()));
                                mm.setParams(params);
                                mm.setReturnType(m.getType().asString());
                                mm.setComment(m.getComment().map(Comment::getContent).orElse("No comment"));
                                m.getBody().ifPresent(b -> mm.setBody(b.toString()));
                                // Repository call detection
                                List<String> repoCalls = new ArrayList<>();
                                m.findAll(MethodCallExpr.class).forEach(mc -> mc.getScope().ifPresent(s -> {
                                    String scope = s.toString();
                                    if (scope.toLowerCase().endsWith("repository") || scope.toLowerCase().contains("repo")) {
                                        repoCalls.add(mc.getNameAsString());
                                    }
                                }));
                                mm.setRepositoryCalls(repoCalls);

                                // Domain keywords (quick heuristic)
                                try {
                                    String fileText = new String(Files.readAllBytes(p)).toLowerCase();
                                    List<String> domain = new ArrayList<>();
                                    for (String w : List.of("inventory", "stock", "warehouse", "product", "order", "user", "auth", "token", "lock", "redis")) {
                                        if (fileText.contains(w)) domain.add(w);
                                    }
                                    mm.setDomainKeywords(domain);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                                methods.add(mm);
                            });

                            cm.setMethods(methods);
                            Map<String, Object> extra = new HashMap<>();
                            extra.put("filePath", p.toString());
                            cm.setExtra(extra);
                            classes.add(cm);
                        });
                    } catch (Exception ex) {
                        log.warn("parse file {} failed: {}", p, ex.getMessage());
                    }
                });

        pm.setClasses(classes);
        return pm;
    }

    private String sanitize(String in) {
        if (in==null) return "";
        String s = in.trim().replaceAll("[\\\"\\r\\n\\t\\u0000{}]","");
        int idx = Math.max(s.indexOf("http://"), s.indexOf("https://"));
        if (idx>=0) s = s.substring(idx);
        return s;
    }

    private String extractRepoName(String url) {
        String u = url.endsWith("/") ? url.substring(0,url.length()-1) : url;
        if (u.endsWith(".git")) u = u.substring(0,u.length()-4);
        int last = u.lastIndexOf('/');
        if (last>=0) return u.substring(last+1);
        return "repo_"+UUID.randomUUID();
    }

    private String detectType(ClassOrInterfaceDeclaration c) {
        String n = c.getNameAsString().toLowerCase();
        if (n.contains("controller")) return "Controller";
        if (n.contains("service")) return "Service";
        if (n.contains("repository") || n.contains("repo")) return "Repository";
        if (n.contains("entity") || n.contains("model")) return "Entity";
        return "Model";
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
    }
