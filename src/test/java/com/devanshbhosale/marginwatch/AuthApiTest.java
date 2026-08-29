package com.devanshbhosale.marginwatch;

import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.devanshbhosale.marginwatch.domain.Vendor;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AuthApiTest extends IntegrationTestBase {

    @Test
    void loginWithSeededDemoAdminReturnsTokenAndUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.demo@marginwatch.in\",\"password\":\"Demo@Admin2026\"}"))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertFalse(body.path("token").asText().isEmpty());
        assertEquals("admin.demo@marginwatch.in", body.path("user").path("email").asText());
        assertEquals("ADMIN", body.path("user").path("role").asText());
        assertTrue(body.path("user").path("isDemo").asBoolean());
    }

    @Test
    void loginWithBadPasswordReturns401WithErrorJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.demo@marginwatch.in\",\"password\":\"wrong\"}"))
                .andReturn();
        assertEquals(401, result.getResponse().getStatus());
        assertFalse(json(result).path("error").asText().isEmpty());
    }

    @Test
    void accountWithTokenReturnsProfile() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/account")
                        .header("Authorization", bearer(admin())))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertEquals("priya.nair@marginwatch.in", body.path("email").asText());
        assertEquals("Priya Nair", body.path("fullName").asText());
        assertEquals("ADMIN", body.path("role").asText());
        assertFalse(body.path("isDemo").asBoolean());
    }

    @Test
    void accountWithoutTokenIs401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/account")).andReturn();
        assertEquals(401, result.getResponse().getStatus());
    }

    @Test
    void viewerCanReadButEveryWriteIs403() throws Exception {
        MvcResult read = mockMvc.perform(get("/api/vendors")
                        .header("Authorization", bearer(viewer())))
                .andReturn();
        assertEquals(200, read.getResponse().getStatus());

        Vendor vendor = newVendor();
        MvcResult deniedMapping = mockMvc.perform(put("/api/vendors/" + vendor.getCode() + "/mapping")
                        .header("Authorization", bearer(viewer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andReturn();
        assertEquals(403, deniedMapping.getResponse().getStatus());
        assertEquals("Forbidden", json(deniedMapping).path("error").asText());

        MvcResult deniedUpload = mockMvc.perform(multipart("/api/files")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "x.csv", "text/csv", "SKU\n".getBytes()))
                        .param("vendorCode", vendor.getCode())
                        .header("Authorization", bearer(viewer())))
                .andReturn();
        assertEquals(403, deniedUpload.getResponse().getStatus());

        MvcResult deniedSkip = mockMvc.perform(post("/api/rows/1/skip")
                        .header("Authorization", bearer(viewer())))
                .andReturn();
        assertEquals(403, deniedSkip.getResponse().getStatus());
    }

    @Test
    void unauthenticatedGetIs401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/files")).andReturn();
        assertEquals(401, result.getResponse().getStatus());
    }
}
