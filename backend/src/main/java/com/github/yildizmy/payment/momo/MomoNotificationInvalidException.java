package com.github.yildizmy.payment.momo;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Invalid MoMo notification")
final class MomoNotificationInvalidException extends RuntimeException {
}
