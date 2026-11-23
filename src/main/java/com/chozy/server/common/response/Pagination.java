package com.chozy.server.common.response;

public record Pagination(int page, int size, long totalElements, long totalPages, boolean hasNext) {
  public static final int MAX_SIZE = 100;

  public static Pagination of(int page, long pageIndex, int size, long totalElements) {
    long totalPages = Math.ceilDiv(totalElements, size);
    return new Pagination(page, size, totalElements, totalPages, pageIndex + 1 < totalPages);
  }
}
