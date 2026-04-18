package com.example.llmragplatform.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-admin-password",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-operator-password",
        "spring.datasource.url=jdbc:h2:mem:authtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `issue auth token returns bearer token for valid credentials`() {
        val token = mockMvc.perform(
            post("/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "test-admin",
                      "password": "test-admin-password"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.username").value("test-admin"))
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.accessToken") }

        mockMvc.perform(
            get("/v1/knowledge-documents")
                .header("Authorization", "Bearer $token")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `issue auth token returns 401 for invalid credentials`() {
        mockMvc.perform(
            post("/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "test-admin",
                      "password": "wrong-password"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid username or password"))
    }
}
