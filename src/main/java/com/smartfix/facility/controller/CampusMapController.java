package com.smartfix.facility.controller;

import com.smartfix.facility.service.CampusMapService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CampusMapController {

    private final CampusMapService campusMapService;

    public CampusMapController(CampusMapService campusMapService) {
        this.campusMapService = campusMapService;
    }

    @GetMapping("/campus-map")
    public String campusMap(Model model) {
        model.addAttribute(
            "facilities",
            campusMapService.listFacilities());

        return "facility/map";
    }
}
