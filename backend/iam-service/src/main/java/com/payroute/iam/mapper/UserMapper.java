package com.payroute.iam.mapper;

import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.dto.response.UserResponse;
import com.payroute.iam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "pinSet",
            expression = "java(user.getTransactionPinHash() != null && !user.getTransactionPinHash().isBlank())")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    default PagedResponse<UserResponse> toPagedResponse(Page<User> page) {
        return PagedResponse.<UserResponse>builder()
                .content(toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
