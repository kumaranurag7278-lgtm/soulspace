package com.soulspace.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    /** Deep link to a private consultation room; the SPA reads the code from the URL. */
    @GetMapping("/room/private/{code}")
    public String room() {
        return "forward:/index.html";
    }
}
