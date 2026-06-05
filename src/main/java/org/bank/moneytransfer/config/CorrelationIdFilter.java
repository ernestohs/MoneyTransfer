package org.bank.moneytransfer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationIds.HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = CorrelationIds.newId();
        }
        request.setAttribute(CorrelationIds.ATTRIBUTE, correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId);
        filterChain.doFilter(request, response);
    }
}
