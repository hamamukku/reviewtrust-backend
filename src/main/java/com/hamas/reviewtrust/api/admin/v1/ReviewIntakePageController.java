package com.hamas.reviewtrust.api.admin.v1;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReviewIntakePageController {

    @GetMapping("/admin/intake-review")
    public String intakeReviewPage() {
        return "forward:/admin/intake-review/index.html";
    }
}

