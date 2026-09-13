package com.arms.api.multiagent.model.dto;

import com.arms.egovframework.javaservice.aigenerate.l_query.model.UserQueryDTO;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MultiAgentDTO extends UserQueryDTO {
}
