package dev.ordonselli.raidboard.group;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface MembershipMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", source = "user.displayName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    MemberResponse toMemberResponse(Membership membership);
}
