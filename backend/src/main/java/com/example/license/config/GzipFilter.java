package com.example.license.config;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * Filter to automatically decompress GZIP requests from the SDK.
 * This is essential for handling high-volume event data efficiently.
 * Updated for Jakarta EE (Spring Boot 3+).
 */
@Component
public class GzipFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String contentEncoding = request.getHeader("Content-Encoding");

        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            filterChain.doFilter(new GzipRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static class GzipRequestWrapper extends HttpServletRequestWrapper {
        public GzipRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            final GZIPInputStream gzipInputStream = new GZIPInputStream(super.getInputStream());
            return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    return gzipInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return false;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }
            };
        }
    }
}
