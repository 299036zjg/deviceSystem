package com.sems.domain.converter;

import com.sems.domain.RequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 报修单状态持久化转换：数据库保存中文状态值（设计文档 4.3）。
 */
@Converter(autoApply = true)
public class RequestStatusConverter implements AttributeConverter<RequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(RequestStatus attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public RequestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RequestStatus.from(dbData);
    }
}
