package com.queueforge.api.job;

import com.queueforge.api.config.AppPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    public JobController(JobService jobService) { this.jobService = jobService; }

    @GetMapping
    public List<JobResponse> list(@AuthenticationPrincipal AppPrincipal principal,
                                  @RequestParam(required = false) JobStatus status,
                                  @RequestParam(required = false) String search) {
        return jobService.list(principal, status, search);
    }

    @PostMapping
    public JobResponse create(@AuthenticationPrincipal AppPrincipal principal, @Valid @RequestBody JobRequest request) {
        return jobService.create(principal, request);
    }

    @GetMapping("/{id}")
    public JobResponse get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return jobService.get(principal, id);
    }

    @GetMapping("/{id}/events")
    public List<JobService.JobEventView> events(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return jobService.events(principal, id);
    }

    @PostMapping("/{id}/retry")
    public JobResponse retry(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return jobService.retry(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public JobResponse cancel(@AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID id) {
        return jobService.cancel(principal, id);
    }
}

