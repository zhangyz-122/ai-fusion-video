package com.stonewu.fusion.controller.ai;

import com.stonewu.fusion.controller.ai.vo.ApiConfigPageReqVO;
import com.stonewu.fusion.controller.ai.vo.ApiConfigRespVO;
import com.stonewu.fusion.controller.ai.vo.ApiConfigSaveReqVO;
import com.stonewu.fusion.convert.ai.ApiConfigConvert;
import com.stonewu.fusion.entity.ai.ApiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SW-T17(P0):API 配置密钥泄露修复的回归测试。
 * <p>
 * 红队报告 A-1:/get、/list 缺少 ADMIN 权限注解,任意登录用户可明文拉取全部
 * apiKey/appSecret/proxyPassword;A-2:实体 toString 含密钥。
 */
class ApiConfigSecretExposureTests {

    @Test
    void everyEndpointRequiresAdminRole() throws Exception {
        Map<String, Method> endpoints = Map.of(
                "get", ApiConfigController.class.getMethod("get", Long.class),
                "list", ApiConfigController.class.getMethod("list"),
                "page", ApiConfigController.class.getMethod("page", ApiConfigPageReqVO.class),
                "create", ApiConfigController.class.getMethod("create", ApiConfigSaveReqVO.class),
                "update", ApiConfigController.class.getMethod("update", ApiConfigSaveReqVO.class),
                "delete", ApiConfigController.class.getMethod("delete", Long.class),
                "remoteModels", ApiConfigController.class.getMethod("remoteModels", Long.class),
                "testComfyUiConnectivity", ApiConfigController.class.getMethod("testComfyUiConnectivity", Long.class));

        for (Map.Entry<String, Method> entry : endpoints.entrySet()) {
            PreAuthorize annotation = entry.getValue().getAnnotation(PreAuthorize.class);
            assertThat(annotation).as("端点 %s 必须声明 @PreAuthorize", entry.getKey()).isNotNull();
            assertThat(annotation.value())
                    .as("端点 %s 必须仅允许 ADMIN 角色", entry.getKey())
                    .isEqualTo("hasRole('ADMIN')");
        }
    }

    @Test
    void controllerGetAndListAreAnnotatedDirectly() throws Exception {
        // 报告攻击路径的原始入口:普通用户 token 调 /get 与 /list 拉取密钥
        Method get = ApiConfigController.class.getMethod("get", Long.class);
        Method list = ApiConfigController.class.getMethod("list");
        assertThat(get.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(list.getAnnotation(PreAuthorize.class)).isNotNull();
    }

    @Test
    void respVoNoLongerCarriesSecretFields() {
        assertThatThrownBy(() -> ApiConfigRespVO.class.getDeclaredField("apiKey"))
                .as("RespVO 不得回传 apiKey")
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> ApiConfigRespVO.class.getDeclaredField("appSecret"))
                .as("RespVO 不得回传 appSecret")
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> ApiConfigRespVO.class.getDeclaredField("proxyPassword"))
                .as("RespVO 不得回传 proxyPassword")
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void convertOutputContainsNoSecretValues() {
        ApiConfig config = ApiConfig.builder()
                .id(1L).name("测试配置").platform("openai_compatible")
                .apiKey("sk-real-secret-key").appSecret("app-secret-value")
                .proxyPassword("proxy-password-value")
                .build();

        ApiConfigRespVO respVO = ApiConfigConvert.INSTANCE.convert(config);
        List<ApiConfigRespVO> list = ApiConfigConvert.INSTANCE.convertList(List.of(config));

        assertThat(respVO.getName()).isEqualTo("测试配置");
        assertThat(toStringOf(respVO))
                .doesNotContain("sk-real-secret-key", "app-secret-value", "proxy-password-value");
        assertThat(list).hasSize(1);
        assertThat(toStringOf(list.get(0)))
                .doesNotContain("sk-real-secret-key", "app-secret-value", "proxy-password-value");
    }

    @Test
    void entityToStringExcludesSecrets() {
        ApiConfig config = ApiConfig.builder()
                .id(2L).name("日志安全").apiKey("sk-logged-if-leaked")
                .appSecret("secret-if-logged").proxyPassword("proxy-if-logged")
                .build();

        String text = config.toString();

        assertThat(text).doesNotContain("sk-logged-if-leaked");
        assertThat(text).doesNotContain("secret-if-logged");
        assertThat(text).doesNotContain("proxy-if-logged");
    }

    private String toStringOf(Object value) {
        return value == null ? "null" : value.toString();
    }
}
