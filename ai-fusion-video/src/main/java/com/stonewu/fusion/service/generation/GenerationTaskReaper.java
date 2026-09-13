package com.stonewu.fusion.service.generation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.mapper.generation.ImageTaskMapper;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 滞留任务回收：排队/执行中超过阈值的图片与视频任务标记为失败，
 * 避免仪表盘"进行中"列表被永久卡死的任务污染。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationTaskReaper {

    /** 滞留阈值：超过该时长仍处于排队/执行中的任务视为卡死。 */
    private static final int STALE_HOURS = 2;

    private static final int FAILURE_STATUS = 3;

    private static final String TIMEOUT_MESSAGE = "任务滞留超过 " + STALE_HOURS + " 小时，已自动标记失败";

    private final ImageTaskMapper imageTaskMapper;
    private final VideoTaskMapper videoTaskMapper;

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reapStaleTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(STALE_HOURS);

        List<Long> staleImageIds = imageTaskMapper.selectList(new LambdaQueryWrapper<ImageTask>()
                .select(ImageTask::getId)
                .in(ImageTask::getStatus, 0, 1)
                .lt(ImageTask::getCreateTime, threshold)).stream().map(ImageTask::getId).toList();
        if (!staleImageIds.isEmpty()) {
            int updated = imageTaskMapper.update(null, new LambdaUpdateWrapper<ImageTask>()
                    .in(ImageTask::getId, staleImageIds)
                    .set(ImageTask::getStatus, FAILURE_STATUS)
                    .set(ImageTask::getErrorMsg, TIMEOUT_MESSAGE));
            log.warn("[TaskReaper] 滞留图片任务已标记失败: count={}", updated);
        }

        List<Long> staleVideoIds = videoTaskMapper.selectList(new LambdaQueryWrapper<VideoTask>()
                .select(VideoTask::getId)
                .in(VideoTask::getStatus, 0, 1)
                .lt(VideoTask::getCreateTime, threshold)).stream().map(VideoTask::getId).toList();
        if (!staleVideoIds.isEmpty()) {
            int updated = videoTaskMapper.update(null, new LambdaUpdateWrapper<VideoTask>()
                    .in(VideoTask::getId, staleVideoIds)
                    .set(VideoTask::getStatus, FAILURE_STATUS)
                    .set(VideoTask::getErrorMsg, TIMEOUT_MESSAGE));
            log.warn("[TaskReaper] 滞留视频任务已标记失败: count={}", updated);
        }
    }
}
