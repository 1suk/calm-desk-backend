package com.code808.calmdesk.global.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import com.code808.calmdesk.domain.auth.service.RefreshTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = getJwtFromRequest(request);

            if (StringUtils.hasText(token)) {
                Optional<Claims> claimsOpt = jwtTokenProvider.validateToken(token);

                if (claimsOpt.isEmpty()) {
                    handleException(response, "INVAILD_TOKEN", "유효하지 않은 토큰입니다.");
                    return;
                }

                Claims claims = claimsOpt.get();
                String email = jwtTokenProvider.getEmailFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

               if(!"TEMP".equals(role)){
                   long tokenVersion = Optional.ofNullable(claims.get("tokenVersion", Long.class))
                           .orElse(0L);
                   long redisVersion = refreshTokenService.getTokenVersion(email);

                   if(redisVersion == 0L){
                       handleException(response, "DUPLICATE_LOGIN", "다른 기기에서 로그인되었거나 세션이 만료되었습니다.");
                       return;
                   }

                   if(tokenVersion != redisVersion){
                       handleException(response, "DUPLICATE_LOGIN", "다른 기기에서 로그인되었습니다. 다시 로그인해주세요");
                       return;
                   }

                   SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                   UsernamePasswordAuthenticationToken authentication =
                           new UsernamePasswordAuthenticationToken(
                                   email,
                                   null,
                                   Collections.singletonList(authority)
                           );

                   authentication.setDetails(
                           new WebAuthenticationDetailsSource().buildDetails(request)
                   );

                   SecurityContextHolder.getContext().setAuthentication(authentication);
               }
            }
        } catch (Exception e) {
            logger.error("JWT 인증 에러", e);
            handleException(response, "INVALID_TOKEN", "인증 처리 중 오류가 발생했습니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleException(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"code\": \"%s\", \"message\": \"%s\"}", code, message));
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 제거
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // SSE 구독 요청 및 Swagger 관련 요청은 필터를 거치지 않음
        return path.startsWith("/subscribe")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars");
    }
}
