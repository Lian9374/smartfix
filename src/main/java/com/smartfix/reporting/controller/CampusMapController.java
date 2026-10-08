package com.smartfix.reporting.controller;

import com.smartfix.facility.service.CampusMapService;
import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.auth.security.SmartFixUserDetails;
import com.smartfix.reporting.dto.CampusMapSnapshot;
import com.smartfix.reporting.service.CampusMapStatusService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CampusMapController {
    private final CampusMapService directory;
    private final CampusMapStatusService statuses;

    public CampusMapController(CampusMapService directory, CampusMapStatusService statuses) {
        this.directory = directory; this.statuses = statuses;
    }

    @GetMapping("/campus-map")
    public String campusMap(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String building,
            @RequestParam(defaultValue = "") String floor,
            @RequestParam(required = false) FacilityStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @AuthenticationPrincipal SmartFixUserDetails principal, Model model) {
        Long actorId = principal == null ? null : principal.getUserId();
        var campus = directory.browse(actorId, q, building, floor, status, page, size);
        model.addAttribute("campus", campus);
        model.addAttribute("atlas", statuses.snapshot(actorId));
        model.addAttribute("facilities", campus.facilities().getContent());
        model.addAttribute("pagination", campus.facilities());
        model.addAttribute("query", q.trim());
        model.addAttribute("selectedBuilding", building.trim());
        model.addAttribute("selectedFloor", floor.trim());
        model.addAttribute("selectedStatus", status);
        return "facility/map";
    }

    @GetMapping(value = "/campus-map/status", produces = "application/json")
    public ResponseEntity<CampusMapSnapshot> statuses(@AuthenticationPrincipal SmartFixUserDetails principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(statuses.snapshot(principal == null ? null : principal.getUserId()));
    }
}
