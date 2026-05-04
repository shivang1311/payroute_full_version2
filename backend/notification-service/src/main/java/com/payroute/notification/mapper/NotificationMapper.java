package com.payroute.notification.mapper;

import com.payroute.notification.dto.request.NotificationRequest;
import com.payroute.notification.dto.response.NotificationResponse;
import com.payroute.notification.dto.response.PagedResponse;
import com.payroute.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    NotificationResponse toResponse(Notification entity);

    List<NotificationResponse> toResponseList(List<Notification> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isRead", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    Notification toEntity(NotificationRequest request);

    default PagedResponse<NotificationResponse> toPagedResponse(Page<Notification> page) {
        return PagedResponse.<NotificationResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
