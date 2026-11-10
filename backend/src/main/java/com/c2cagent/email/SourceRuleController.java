package com.c2cagent.email;

import com.c2cagent.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/source-rules")
@Tag(name = "Email source rules")
public class SourceRuleController {
    private final SourceRuleService service;

    public SourceRuleController(SourceRuleService service) { this.service = service; }

    @GetMapping
    public List<SourceRuleDtos.RuleDto> list(CurrentUser user) {
        return service.list(user.workspaceId()).stream().map(SourceRuleDtos.RuleDto::from).toList();
    }

    @GetMapping("/precedence")
    public Map<String, String> precedence() {
        return Map.of("precedence", SourceRuleDtos.PRECEDENCE);
    }

    @PostMapping
    public ResponseEntity<SourceRuleDtos.RuleDto> create(CurrentUser user,
                                                         @Valid @RequestBody SourceRuleDtos.RuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceRuleDtos.RuleDto.from(service.create(user, req)));
    }

    @PutMapping("/{id}")
    public SourceRuleDtos.RuleDto update(CurrentUser user, @PathVariable UUID id,
                                         @Valid @RequestBody SourceRuleDtos.RuleRequest req) {
        return SourceRuleDtos.RuleDto.from(service.update(user, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @Operation(summary = "Export all rules as JSON (re-importable)")
    public ResponseEntity<List<SourceRuleDtos.RuleRequest>> export(CurrentUser user) {
        var body = service.list(user.workspaceId()).stream()
                .map(r -> new SourceRuleDtos.RuleRequest(r.getRuleType(), r.getMatchValue(), r.getGmailAccountId(),
                        r.getPriority(), r.isEnabled(), r.getNote()))
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"c2c-source-rules.json\"")
                .body(body);
    }

    @PostMapping("/import")
    public SourceRuleDtos.ImportResult importRules(CurrentUser user,
                                                   @Valid @RequestBody SourceRuleDtos.ImportRequest req) {
        return service.importRules(user, req.rules());
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Test which decision the rules give for a sender (no data is changed)")
    public SourceRuleDtos.EvaluateResponse evaluate(CurrentUser user,
                                                    @Valid @RequestBody SourceRuleDtos.EvaluateRequest req) {
        var r = service.evaluate(user.workspaceId(), req.fromAddress(), req.labelIds(), req.gmailAccountId());
        return new SourceRuleDtos.EvaluateResponse(r.decision(), r.ruleId(), r.explanation(),
                r.decision().continuesToClassification());
    }
}
