package com.smartfix.facility.controller;

import com.smartfix.facility.service.CampusMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
    controllers = CampusMapController.class,
    excludeAutoConfiguration =
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class CampusMapControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CampusMapService campusMapService;

    @Test
    void campusMapShowsAvailableFacilities() throws Exception {
        when(campusMapService.listFacilities())
            .thenReturn(List.of());

        mvc.perform(get("/campus-map"))
            .andExpect(status().isOk())
            .andExpect(view().name("facility/map"))
            .andExpect(model().attributeExists("facilities"));

        verify(campusMapService).listFacilities();
    }
}
