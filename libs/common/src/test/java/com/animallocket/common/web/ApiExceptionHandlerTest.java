package com.animallocket.common.web;

import com.animallocket.common.Headers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {
        @GetMapping("/api")
        String api() {
            throw ApiException.conflict("ALIAS_TAKEN", "Biệt danh đã có người dùng");
        }

        @GetMapping("/missing")
        String missing() {
            throw ApiException.notFound("Bài đăng");
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/number")
        String number(@RequestParam int page) {
            return "p" + page;
        }

        @GetMapping("/me")
        String me(@RequestHeader(Headers.PUBLIC_ID) String me) {
            return me;
        }

        @GetMapping("/other-header")
        String otherHeader(@RequestHeader("X-Device") String device) {
            return device;
        }

        /** Không có @Validated ở class: Spring MVC 6.1 tự kiểm ràng buộc và ném HandlerMethodValidationException. */
        @GetMapping("/sized")
        String sized(@RequestParam @Size(max = 5) String q) {
            return q;
        }

        @GetMapping("/dup")
        String dup() {
            throw new DataIntegrityViolationException("Duplicate entry 'secret@x' for key 'uk_email'");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("db password=hunter2");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator())
            .build();

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        return v;
    }

    @Test
    void apiExceptionKeepsStatusAndCodeAsProblemJson() throws Exception {
        mvc.perform(get("/api"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.code").value("ALIAS_TAKEN"))
                .andExpect(jsonPath("$.detail").value("Biệt danh đã có người dùng"));
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void beanValidationFailureIsBadRequest() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("name")));
    }

    @Test
    void unreadableBodyIsBadRequest() throws Exception {
        mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void typeMismatchAndMissingParamAreBadRequest() throws Exception {
        mvc.perform(get("/number").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("page")));
        mvc.perform(get("/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Thiếu tham số page"));
    }

    @Test
    void missingPublicIdMeansProfileRequiredButOtherHeadersAreBadRequest() throws Exception {
        mvc.perform(get("/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE_REQUIRED"));
        mvc.perform(get("/other-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void builtInMethodValidationIsBadRequestNamingTheParameter() throws Exception {
        mvc.perform(get("/sized").param("q", "abc"))
                .andExpect(status().isOk());
        mvc.perform(get("/sized").param("q", "too-long-value"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Tham số không hợp lệ: q"));
    }

    @Test
    void dataIntegrityViolationIsConflictWithoutLeakingDbDetails() throws Exception {
        mvc.perform(get("/dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(content().string(not(containsString("secret@x"))));
    }

    @Test
    void unexpectedErrorIs500WithoutInternalMessage() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void springMvcErrorsKeepTheirHttpStatus() throws Exception {
        mvc.perform(put("/api"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
