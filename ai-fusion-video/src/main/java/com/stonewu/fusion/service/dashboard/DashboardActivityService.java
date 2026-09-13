package com.stonewu.fusion.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.controller.dashboard.vo.DashboardActivityVO;
import com.stonewu.fusion.controller.dashboard.vo.DashboardActivityVO.ActivityItem;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.entity.production.ProductionRun;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.mapper.generation.ImageTaskMapper;
import com.stonewu.fusion.mapper.generation.VideoTaskMapper;
import com.stonewu.fusion.mapper.production.ProductionRunMapper;
import com.stonewu.fusion.mapper.production.ProductionTakeMapper;
import com.stonewu.fusion.mapper.project.ProjectMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import com.stonewu.fusion.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 仪表盘活动聚合：真实反映当前用户的进行中任务与待处理事项，
 * 不使用静态数字伪装状态。
 */
@Service
@RequiredArgsConstructor
public class DashboardActivityService {

    private static final int LIMIT = 8;
    private static final int TITLE_MAX = 60;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ImageTaskMapper imageTaskMapper;
    private final VideoTaskMapper videoTaskMapper;
    private final ProductionRunMapper runMapper;
    private final ProductionTakeMapper takeMapper;
    private final ScriptMapper scriptMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;

    public DashboardActivityVO getActivity(Long userId) {
        List<ActivityItem> running = new ArrayList<>();
        List<ActivityItem> pending = new ArrayList<>();

        // 剧本解析（自动分块/AI 解析）进行中
        List<Long> accessibleProjectIds = projectService.listAccessibleByUser(userId).stream()
                .map(Project::getId)
                .toList();
        if (!accessibleProjectIds.isEmpty()) {
            List<Script> parsingScripts = scriptMapper.selectList(new LambdaQueryWrapper<Script>()
                    .in(Script::getProjectId, accessibleProjectIds)
                    .eq(Script::getParsingStatus, 1)
                    .orderByDesc(Script::getUpdateTime)
                    .last("LIMIT " + LIMIT));
            for (Script script : parsingScripts) {
                running.add(ActivityItem.builder()
                        .kind("SCRIPT_PARSE")
                        .refId(script.getId())
                        .title("剧本解析 · " + script.getTitle())
                        .detail(script.getParsingProgress() == null ? "解析中" : script.getParsingProgress())
                        .projectId(script.getProjectId())
                        .createTime(TIME_FORMAT.format(script.getUpdateTime()))
                        .build());
            }
        }

        List<ImageTask> imageTasks = imageTaskMapper.selectList(new LambdaQueryWrapper<ImageTask>()
                .eq(ImageTask::getUserId, userId)
                .in(ImageTask::getStatus, 0, 1)
                .orderByDesc(ImageTask::getCreateTime)
                .last("LIMIT " + LIMIT));
        for (ImageTask task : imageTasks) {
            running.add(ActivityItem.builder()
                    .kind("IMAGE_TASK")
                    .refKey(task.getTaskId())
                    .title(truncate(task.getPrompt()))
                    .detail(Integer.valueOf(1).equals(task.getStatus()) ? "生成中" : "排队中")
                    .projectId(task.getProjectId())
                    .createTime(TIME_FORMAT.format(task.getCreateTime()))
                    .build());
        }

        List<VideoTask> videoTasks = videoTaskMapper.selectList(new LambdaQueryWrapper<VideoTask>()
                .eq(VideoTask::getUserId, userId)
                .in(VideoTask::getStatus, 0, 1)
                .orderByDesc(VideoTask::getCreateTime)
                .last("LIMIT " + LIMIT));
        for (VideoTask task : videoTasks) {
            running.add(ActivityItem.builder()
                    .kind("VIDEO_TASK")
                    .refId(task.getId())
                    .title(truncate(task.getPrompt()))
                    .detail(Integer.valueOf(1).equals(task.getStatus()) ? "生成中" : "排队中")
                    .projectId(task.getProjectId())
                    .createTime(TIME_FORMAT.format(task.getCreateTime()))
                    .build());
        }

        List<ProductionRun> runs = runMapper.selectList(new LambdaQueryWrapper<ProductionRun>()
                .eq(ProductionRun::getUserId, userId)
                .orderByDesc(ProductionRun::getCreateTime)
                .last("LIMIT " + LIMIT));
        for (ProductionRun run : runs) {
            switch (run.getStatus()) {
                case "CREATED", "WAITING_GENERATION" -> running.add(ActivityItem.builder()
                        .kind("PRODUCTION_RUN")
                        .refId(run.getId())
                        .title("生产运行 #" + run.getId())
                        .detail("候选视频生成中")
                        .projectId(run.getProjectId())
                        .createTime(TIME_FORMAT.format(run.getCreateTime()))
                        .build());
                case "QC_PENDING" -> pending.add(ActivityItem.builder()
                        .kind("PRODUCTION_RUN")
                        .refId(run.getId())
                        .title("生产运行 #" + run.getId())
                        .detail("候选视频待质检并选片")
                        .projectId(run.getProjectId())
                        .createTime(TIME_FORMAT.format(run.getCreateTime()))
                        .build());
                case "FAILED" -> pending.add(ActivityItem.builder()
                        .kind("PRODUCTION_RUN")
                        .refId(run.getId())
                        .title("生产运行 #" + run.getId() + " 失败")
                        .detail(truncate(run.getFailureMessage()))
                        .projectId(run.getProjectId())
                        .createTime(TIME_FORMAT.format(run.getUpdateTime()))
                        .build());
                default -> {
                    // SELECTED 等终态不在仪表盘展示
                }
            }
        }

        running.sort(Comparator.comparing(ActivityItem::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        pending.sort(Comparator.comparing(ActivityItem::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return DashboardActivityVO.builder()
                .running(running.stream().limit(LIMIT).toList())
                .pending(pending.stream().limit(LIMIT).toList())
                .build();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= TITLE_MAX ? trimmed : trimmed.substring(0, TITLE_MAX) + "…";
    }
}
