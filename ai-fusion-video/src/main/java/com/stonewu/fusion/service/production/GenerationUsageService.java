package com.stonewu.fusion.service.production;

import com.stonewu.fusion.entity.production.GenerationUsage;
import com.stonewu.fusion.mapper.production.GenerationUsageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerationUsageService {
    private final GenerationUsageMapper usageMapper;

    public GenerationUsage record(GenerationUsage usage) { usageMapper.insert(usage); return usage; }

    public double computeFPR(int totalGenerations, int firstPassSuccesses) {
        return totalGenerations == 0 ? 0 : (double) firstPassSuccesses / totalGenerations;
    }
}
