package com.chozy.server.support;


import com.chozy.server.common.error.BusinessException;
import com.chozy.server.common.error.ErrorCode;
import com.chozy.server.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.test.context.TestComponent
@RestController
public class ProbeController {
  record Payload(@NotBlank String secret) {}

  @GetMapping("/probe/success")
  ApiResponse<?> success() {
    return ApiResponse.success(java.util.Map.of("name", "chozy"));
  }

  @PostMapping(value = "/probe/body", consumes = "application/json")
  void body(@Valid @RequestBody Payload body) {}

  @GetMapping("/probe/number")
  void number(@RequestParam int value) {}

  @GetMapping("/probe/business")
  void business() {
    throw new BusinessException(ErrorCode.DUPLICATE);
  }

  @GetMapping("/probe/forbidden")
  void forbidden() {
    throw new org.springframework.security.access.AccessDeniedException("secret");
  }

  @GetMapping("/probe/failure")
  void failure() {
    throw new IllegalStateException("SQL password=secret-token");
  }
}
