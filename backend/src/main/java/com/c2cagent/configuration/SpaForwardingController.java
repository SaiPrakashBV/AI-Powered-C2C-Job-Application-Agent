package com.c2cagent.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React single-page app for client-side routes (anything without a file extension that is not
 * an API, actuator or OpenAPI path), so deep links such as /resumes work after a browser refresh.
 */
@Controller
public class SpaForwardingController {
    @GetMapping({"/", "/{path:^(?!api|actuator|v3|swagger-ui)[^.]*}", "/{path:^(?!api|actuator|v3|swagger-ui)[^.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
