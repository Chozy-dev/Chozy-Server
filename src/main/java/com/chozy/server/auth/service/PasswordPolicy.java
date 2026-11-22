package com.chozy.server.auth.service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 72;

  private PasswordPolicy() {}

  public static boolean fitsBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= MAX_LENGTH;
  }
}
