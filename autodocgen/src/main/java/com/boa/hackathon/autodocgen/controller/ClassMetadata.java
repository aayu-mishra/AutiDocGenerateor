package com.boa.hackathon.autodocgen.controller;

import lombok.Data;
import java.util.List;
@Data
public class ClassMetadata {
	    private String className;
	    private String packageName;
	    private String type;
	    private List<String> methods;
	    private List<String> fields;
	    private String comment;
	
}
