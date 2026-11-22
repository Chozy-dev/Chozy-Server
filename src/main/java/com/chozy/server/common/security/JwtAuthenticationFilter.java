package com.chozy.server.common.security;

import com.chozy.server.common.error.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final ObjectProvider<JwtTokens> tokens;
  private final SecurityErrorWriter errors;
  public JwtAuthenticationFilter(ObjectProvider<JwtTokens> tokens, SecurityErrorWriter errors) {
    this.tokens = tokens;
    this.errors = errors;
  }
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null) {
      try {
        var jwt = tokens.getIfAvailable();
        if (jwt == null || !header.regionMatches(true, 0, "Bearer ", 0, 7))
          throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION);
        var verified = jwt.verify(header.substring(7), "access");
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedMember(verified.memberId()), null, List.of()));
        SecurityContextHolder.setContext(context);
      } catch (BusinessException ex) {
        SecurityContextHolder.clearContext();
        errors.write(response, ex.errorCode());
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
