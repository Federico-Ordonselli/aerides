package dev.ordonselli.aerides.user;

import org.mapstruct.Mapper;

/** MapStruct generates the implementation at compile time (see target/generated-sources). */
@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
