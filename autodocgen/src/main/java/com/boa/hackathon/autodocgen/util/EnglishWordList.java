package com.boa.hackathon.autodocgen.util;

import java.util.HashSet;
import java.util.Set;

public class EnglishWordList {
    private static final Set<String> COMMON = new HashSet<>();
    static {
        String[] words = new String[] {
                "the","and","for","with","not","this","that","from","into","using","use","create","generate",
                "service","controller","repository","response","request","return","error","status","code",
                "product","inventory","warehouse","stock","user","auth","token","lock","redis","id","name",
                "list","get","set","add","update","delete","find"
        };
        for (String w: words) COMMON.add(w);
    }
    public static boolean isCommonWord(String w){ return COMMON.contains(w); }
}

