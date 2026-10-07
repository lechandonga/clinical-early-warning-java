package com.github.lechandonga.clinicalearlywarningjava.web.dto;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant time) {
}
