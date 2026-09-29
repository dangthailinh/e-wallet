package com.github.yildizmy.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class RefreshTokenHashConverter implements AttributeConverter<RefreshTokenHash, String> {

    @Override
    public String convertToDatabaseColumn(RefreshTokenHash attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public RefreshTokenHash convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RefreshTokenHash.fromDigest(dbData);
    }
}
