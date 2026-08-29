package com.devanshbhosale.marginwatch.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA fallback: any GET that is not /api, an asset, actuator, swagger or the index page
 * itself is forwarded to the React app. Paths with a dot (favicon.ico, hashed bundles)
 * never match, so static resources and 404s keep working.
 */
@Controller
public class SpaController {

    private static final String NOT_APP =
            "^(?!api$|assets$|actuator$|swagger-ui$|v3$|index\\.html$|error$|favicon\\.ico$)[^.]+";

    @GetMapping({"/{path:" + NOT_APP + "}", "/{path:" + NOT_APP + "}/**"})
    public String spa() {
        return "forward:/index.html";
    }
}
