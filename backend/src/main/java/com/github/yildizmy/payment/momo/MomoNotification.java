package com.github.yildizmy.payment.momo;

record MomoNotification(
        String partnerCode,
        String orderId,
        String requestId,
        long amount,
        String orderInfo,
        String orderType,
        long transId,
        int resultCode,
        String message,
        String payType,
        long responseTime,
        String extraData,
        String signature
) {
}
