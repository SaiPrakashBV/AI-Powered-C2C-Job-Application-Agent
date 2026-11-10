package com.c2cagent.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page app for client-side routes so deep links such as /resumes or /jobs/{id} work
 * after a browser refresh. Only extension-less paths up to three segments are forwarded, and never paths under
 * api, actuator, v3 (OpenAPI), swagger-ui or assets — those are real endpoints or static files.
 */
@Controller
public class SpaForwardingController {
    private static final String FIRST = "{a:^(?!api$|actuator$|v3$|swagger-ui$|assets$)[^.]+}";
    private static final String NEXT = "{b:[^.]+}";
    private static final String LAST = "{c:[^.]+}";

    @GetMapping({"/", "/" + FIRST, "/" + FIRST + "/" + NEXT, "/" + FIRST + "/" + NEXT + "/" + LAST})
    public String forward() {
        return "forward:/index.html";
    }
}
