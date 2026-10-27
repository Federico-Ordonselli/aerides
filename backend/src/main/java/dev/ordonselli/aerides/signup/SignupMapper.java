package dev.ordonselli.aerides.signup;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SignupMapper {

    @Mapping(target = "slotId", source = "slot.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", source = "user.displayName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    SignupResponse toResponse(Signup signup);
}
