package com.stonewu.fusion.service.project;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import com.stonewu.fusion.mapper.script.ScriptEpisodeMapper;
import com.stonewu.fusion.mapper.script.ScriptMapper;
import com.stonewu.fusion.mapper.script.ScriptSceneItemMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardEpisodeMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardSceneMapper;
import com.stonewu.fusion.security.SecurityUserDetails;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTests {

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Script.class);
        TableInfoHelper.initTableInfo(assistant, ScriptEpisode.class);
        TableInfoHelper.initTableInfo(assistant, com.stonewu.fusion.entity.script.ScriptSceneItem.class);
        TableInfoHelper.initTableInfo(assistant, Storyboard.class);
        TableInfoHelper.initTableInfo(assistant, com.stonewu.fusion.entity.storyboard.StoryboardEpisode.class);
        TableInfoHelper.initTableInfo(assistant, com.stonewu.fusion.entity.storyboard.StoryboardScene.class);
        TableInfoHelper.initTableInfo(assistant, StoryboardItem.class);
        TableInfoHelper.initTableInfo(assistant, Asset.class);
        TableInfoHelper.initTableInfo(assistant, AssetItem.class);
    }

    @Mock
    private ScriptMapper scriptMapper;
    @Mock
    private ScriptEpisodeMapper scriptEpisodeMapper;
    @Mock
    private ScriptSceneItemMapper scriptSceneItemMapper;
    @Mock
    private StoryboardMapper storyboardMapper;
    @Mock
    private StoryboardEpisodeMapper storyboardEpisodeMapper;
    @Mock
    private StoryboardSceneMapper storyboardSceneMapper;
    @Mock
    private StoryboardItemMapper storyboardItemMapper;
    @Mock
    private AssetMapper assetMapper;
    @Mock
    private AssetItemMapper assetItemMapper;

    private ProjectAccessGuard guard;

    @BeforeEach
    void setUp() {
        ProjectService projectService = mock(ProjectService.class);
        guard = new ProjectAccessGuard(projectService, scriptMapper, scriptEpisodeMapper, scriptSceneItemMapper,
                storyboardMapper, storyboardEpisodeMapper, storyboardSceneMapper, storyboardItemMapper,
                assetMapper, assetItemMapper);
    }

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        new SecurityUserDetails(7L, "uitest", "n/a", 1, null, List.of()), null, "ROLE_USER"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assertScriptDeniesWhenProjectInaccessible() {
        ProjectService projectService = mock(ProjectService.class);
        guard = new ProjectAccessGuard(projectService, scriptMapper, scriptEpisodeMapper, scriptSceneItemMapper,
                storyboardMapper, storyboardEpisodeMapper, storyboardSceneMapper, storyboardItemMapper,
                assetMapper, assetItemMapper);
        when(scriptMapper.selectById(11L)).thenReturn(Script.builder().id(11L).projectId(9L).build());
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> guard.assertScript(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该项目内容");
    }

    @Test
    void assertScriptEpisodeResolvesThroughScriptToProject() {
        ProjectService projectService = mock(ProjectService.class);
        guard = new ProjectAccessGuard(projectService, scriptMapper, scriptEpisodeMapper, scriptSceneItemMapper,
                storyboardMapper, storyboardEpisodeMapper, storyboardSceneMapper, storyboardItemMapper,
                assetMapper, assetItemMapper);
        when(scriptEpisodeMapper.selectById(21L)).thenReturn(ScriptEpisode.builder().id(21L).scriptId(11L).build());
        when(scriptMapper.selectById(11L)).thenReturn(Script.builder().id(11L).projectId(9L).build());
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);

        assertThatCode(() -> guard.assertScriptEpisode(21L)).doesNotThrowAnyException();
    }

    @Test
    void assertStoryboardItemResolvesThroughStoryboardToProject() {
        ProjectService projectService = mock(ProjectService.class);
        guard = new ProjectAccessGuard(projectService, scriptMapper, scriptEpisodeMapper, scriptSceneItemMapper,
                storyboardMapper, storyboardEpisodeMapper, storyboardSceneMapper, storyboardItemMapper,
                assetMapper, assetItemMapper);
        when(storyboardItemMapper.selectById(31L)).thenReturn(StoryboardItem.builder().id(31L).storyboardId(12L).build());
        when(storyboardMapper.selectById(12L)).thenReturn(Storyboard.builder().id(12L).projectId(9L).build());
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);

        assertThatCode(() -> guard.assertStoryboardItems(List.of(31L))).doesNotThrowAnyException();
    }

    @Test
    void assertAssetItemDeniesThroughAssetChain() {
        ProjectService projectService = mock(ProjectService.class);
        guard = new ProjectAccessGuard(projectService, scriptMapper, scriptEpisodeMapper, scriptSceneItemMapper,
                storyboardMapper, storyboardEpisodeMapper, storyboardSceneMapper, storyboardItemMapper,
                assetMapper, assetItemMapper);
        when(assetItemMapper.selectById(41L)).thenReturn(AssetItem.builder().id(41L).assetId(51L).build());
        when(assetMapper.selectById(51L)).thenReturn(Asset.builder().id(51L).projectId(9L).build());
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> guard.assertAssetItem(41L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该项目内容");
    }

    @Test
    void missingEntityIsRejected() {
        when(scriptMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> guard.assertScript(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剧本不存在");
    }
}
