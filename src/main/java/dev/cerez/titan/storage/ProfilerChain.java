package dev.cerez.titan.storage;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ProfilerChain {

    private final String nameProfiler;
    private final ProfilerChain fatherProfiler;
    private final Map<String, ProfilerChain> childProfiler;

}
