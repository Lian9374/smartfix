package com.smartfix.facility.controller;

import com.smartfix.facility.domain.FacilityStatus;
import com.smartfix.facility.service.FacilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
    controllers = FacilityController.class,
    excludeAutoConfiguration =
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
class FacilityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FacilityService facilityService;

    @Test
    void listFacilitiesShowsFacilityManagementPage() throws Exception {
        when(facilityService.listFacilities())
            .thenReturn(List.of());

        mvc.perform(get("/admin/facilities"))
            .andExpect(status().isOk())
            .andExpect(view().name("facility/list"))
            .andExpect(model().attributeExists("facilities"))
            .andExpect(model().attributeExists("facilityStatuses"));

        verify(facilityService).listFacilities();
    }

    @Test
    void changeStatusDelegatesToServiceAndRedirects() throws Exception {
        mvc.perform(post("/admin/facilities/7/status")
                .param("status", "OUT_OF_SERVICE"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/facilities"))
            .andExpect(flash().attribute(
                "successMessage",
                "Facility status updated successfully."));

        verify(facilityService)
            .changeStatus(
                7L,
                FacilityStatus.OUT_OF_SERVICE);
    }
}
