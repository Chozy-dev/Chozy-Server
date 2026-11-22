package com.chozy.server.usage.service;

public class ReportQuotaExceededException extends RuntimeException {
  public ReportQuotaExceededException() {
    super("Monthly report quota exhausted");
  }
}
