package com.stonewu.fusion.service.project;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.GlobalExceptionHandler;
import com.stonewu.fusion.controller.project.ProjectController;
import com.stonewu.fusion.controller.project.vo.ProjectUpdateReqVO;
import com.stonewu.fusion.entity.project.Project;
import com.stonewu.fusion.mapper.project.ProjectMapper;
import com.stonewu.fusion.security.SecurityUserDetails;
import com.stonewu.fusion.service.system.SystemConfigService;
import com.stonewu.fusion.service.team.TeamService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectControllerAccessGuardTests {

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Project.class);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        new SecurityUserDetails(userId, "uitest", "n/a", 1, null, List.of()), null, "ROLE_USER"));
    }

    private void stubPagedProjects(ProjectMapper projectMapper) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Project> result =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        when(projectMapper.selectPage(any(), any())).thenReturn(result);
    }

    private ProjectService projectServiceWith(ProjectMapper projectMapper, TeamService teamService) {
        return new ProjectService(projectMapper, null, null, null, null, null, null, null, null, null, null,
                teamService);
    }

    @Test
    void pageFiltersProjectsByOwnerWhenUserHasNoTeam() {
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        TeamService teamService = mock(TeamService.class);
        ProjectService service = projectServiceWith(projectMapper, teamService);
        when(teamService.getCurrentTeamIdByUser(7L)).thenReturn(null);
        stubPagedProjects(projectMapper);

        service.page(1, 10, 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Project>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(projectMapper).selectPage(any(), wrapperCaptor.capture());
        LambdaQueryWrapper<Project> ownerWrapper = (LambdaQueryWrapper<Project>) wrapperCaptor.getValue();
        assertThat(ownerWrapper.getSqlSegment().toLowerCase())
                .contains("owner_type")
                .contains("owner_id");
        assertThat(ownerWrapper.getParamNameValuePairs()).containsValue(7L);
    }

    @Test
    void pageFiltersProjectsByTeamWhenUserHasTeam() {
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        TeamService teamService = mock(TeamService.class);
        ProjectService service = projectServiceWith(projectMapper, teamService);
        when(teamService.getCurrentTeamIdByUser(7L)).thenReturn(5L);
        when(teamService.listMemberUserIds(5L)).thenReturn(List.of(7L, 8L));
        stubPagedProjects(projectMapper);

        service.page(1, 10, 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Project>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(projectMapper).selectPage(any(), wrapperCaptor.capture());
        LambdaQueryWrapper<Project> teamWrapper = (LambdaQueryWrapper<Project>) wrapperCaptor.getValue();
        assertThat(teamWrapper.getSqlSegment().toLowerCase())
                .contains("owner_type")
                .contains("owner_id");
        assertThat(teamWrapper.getParamNameValuePairs())
                .containsValue(5L)
                .containsValue(7L)
                .containsValue(8L);
    }

    @Test
    void getRejectsInaccessibleProject() {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        loginAs(7L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.get(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该项目")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(projectService, never()).getById(9L);
    }

    @Test
    void getReturnsProjectWhenAccessible() {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        loginAs(7L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(true);
        Project project = new Project();
        when(projectService.getById(9L)).thenReturn(project);

        assertThat(controller.get(9L).getData()).isSameAs(project);
    }

    @Test
    void workspaceOverviewRejectsInaccessibleProject() {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        loginAs(7L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.getWorkspaceOverview(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该项目")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(projectService, never()).getWorkspaceOverview(9L);
    }

    @Test
    void updateRejectsInaccessibleProject() {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        loginAs(7L);
        ProjectUpdateReqVO reqVO = new ProjectUpdateReqVO();
        reqVO.setId(9L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.update(reqVO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权修改该项目")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(projectService, never()).update(any(Project.class));
    }

    @Test
    void deleteRejectsInaccessibleProject() {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        loginAs(7L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> controller.delete(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权删除该项目")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(projectService, never()).delete(9L);
    }

    @Test
    void getInaccessibleProjectRespondsWithHttp403AndUnchangedMessage() throws Exception {
        ProjectService projectService = mock(ProjectService.class);
        ProjectController controller = new ProjectController(projectService, mock(SystemConfigService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        loginAs(7L);
        when(projectService.canAccessProject(9L, 7L)).thenReturn(false);

        mockMvc.perform(get("/api/project/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("无权访问该项目"));
    }
}
