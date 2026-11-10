package com.c2cagent.users;

import com.c2cagent.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings")
public class SettingsController {
    private final SettingsService service;

    public SettingsController(SettingsService service) { this.service = service; }

    @GetMapping
    public SettingsDtos.SettingsResponse get(CurrentUser user) {
        return SettingsDtos.SettingsResponse.from(service.getOrCreate(user.workspaceId()));
    }

    @PutMapping
    public SettingsDtos.SettingsResponse update(CurrentUser user,
                                                @Valid @RequestBody SettingsDtos.UpdateSettingsRequest req) {
        return SettingsDtos.SettingsResponse.from(service.update(user, req));
    }

    @PostMapping("/emergency-stop")
    @Operation(summary = "Engage or release the global emergency stop for outbound sending")
    public SettingsDtos.SettingsResponse emergencyStop(CurrentUser user,
                                                       @Valid @RequestBody SettingsDtos.EmergencyStopRequest req) {
        return SettingsDtos.SettingsResponse.from(service.setEmergencyStop(user, req.engaged(), req.reason()));
    }
}
