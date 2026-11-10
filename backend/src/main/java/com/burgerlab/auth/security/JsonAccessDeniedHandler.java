package com.burgerlab.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter errorWriter;

    public JsonAccessDeniedHandler(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        if (accessDeniedException instanceof CsrfException) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN,
                    "CSRF_INVALID", "CSRF 校验失败，请刷新页面后重试");
            return;
        }
        errorWriter.write(request, response, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "没有权限执行该操作");
    }
}
