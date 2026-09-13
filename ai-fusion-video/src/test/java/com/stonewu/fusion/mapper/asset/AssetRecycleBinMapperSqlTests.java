package com.stonewu.fusion.mapper.asset;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回收站 Mapper SQL 契约测试（不依赖数据库）。
 * 资产实体 @TableLogic 会给 BaseMapper 注入方法自动追加 deleted = 0，
 * 回收站依赖自定义 SQL 显式命中软删行——这里用 MyBatis 的动态 SQL 解析器
 * 验证注解 SQL 的关键语义，防止误改回“查不到软删行”或误用逻辑删除。
 */
class AssetRecycleBinMapperSqlTests {

    private final XMLLanguageDriver languageDriver = new XMLLanguageDriver();
    private final Configuration configuration = new Configuration();

    private String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AssetMapper.class.getMethod(methodName, parameterTypes);
        return String.join("", method.getAnnotation(Select.class).value());
    }

    private BoundSql bind(String sql, Map<String, Object> params) {
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Map.class);
        return sqlSource.getBoundSql(params);
    }

    @Test
    void selectDeletedPageStartsWithScriptTagAndScopesDeletedRows() throws Exception {
        String sql = selectSql("selectDeletedPage", IPage.class, Collection.class);
        // 文本块中的 <script> 必须顶格，否则 MyBatis 不按动态 SQL 解析
        assertThat(sql.trim()).startsWith("<script>").endsWith("</script>");

        BoundSql boundSql = bind(sql, Map.of("projectIds", List.of(3L, 5L)));
        assertThat(boundSql.getSql())
                .contains("deleted = 1")
                .contains("project_id IN")
                .doesNotContain("deleted = 0");
    }

    @Test
    void selectDeletedPageExpandsProjectIdForeach() throws Exception {
        String sql = selectSql("selectDeletedPage", IPage.class, Collection.class);

        BoundSql boundSql = bind(sql, Map.of("projectIds", List.of(7L, 9L)));

        // foreach 展开为两个参数占位
        assertThat(boundSql.getParameterMappings()).hasSize(2);
    }

    @Test
    void selectDeletedByIdFiltersDeletedRowsOnly() throws Exception {
        String sql = selectSql("selectDeletedById", Long.class);

        BoundSql boundSql = bind(sql, Map.of("id", 12L));
        assertThat(boundSql.getSql())
                .contains("deleted = 1")
                .doesNotContain("deleted = 0");
    }

    @Test
    void restoreByIdClearsDeletedFlagOnlyForDeletedRows() throws Exception {
        Method method = AssetMapper.class.getMethod("restoreById", Long.class);
        String sql = String.join("", method.getAnnotation(Update.class).value());

        BoundSql boundSql = bind(sql, Map.of("id", 12L));
        assertThat(boundSql.getSql())
                .startsWith("UPDATE afv_asset")
                .contains("SET deleted = 0")
                .contains("deleted = 1");
    }

    @Test
    void deletePhysicallyByIdIsPhysicalDelete() throws Exception {
        Method method = AssetMapper.class.getMethod("deletePhysicallyById", Long.class);
        String sql = String.join("", method.getAnnotation(Delete.class).value());

        BoundSql boundSql = bind(sql, Map.of("id", 12L));
        assertThat(boundSql.getSql())
                .startsWith("DELETE FROM afv_asset")
                .doesNotContain("UPDATE");
    }

    @Test
    void deletePhysicallyByAssetIdIsPhysicalDeleteOnItemTable() throws Exception {
        Method method = AssetItemMapper.class.getMethod("deletePhysicallyByAssetId", Long.class);
        String sql = String.join("", method.getAnnotation(Delete.class).value());

        BoundSql boundSql = bind(sql, Map.of("assetId", 12L));
        assertThat(boundSql.getSql())
                .startsWith("DELETE FROM afv_asset_item")
                .contains("asset_id =");
    }

    // ========== 恢复子资产 / 子资产全量查询（含软删行） ==========

    @Test
    void restoreDeletedByAssetIdClearsDeletedFlagOnItemTable() throws Exception {
        Method method = AssetItemMapper.class.getMethod("restoreDeletedByAssetId", Long.class);
        String sql = String.join("", method.getAnnotation(Update.class).value());

        BoundSql boundSql = bind(sql, Map.of("assetId", 12L));
        assertThat(boundSql.getSql())
                .startsWith("UPDATE afv_asset_item")
                .contains("SET deleted = 0")
                .contains("deleted = 1")
                .contains("asset_id =");
    }

    @Test
    void selectPhysicallyByAssetIdHitsDeletedRowsToo() throws Exception {
        // 收集媒体文件地址必须能触达已软删子资产，@TableLogic 追加的 deleted = 0 会漏行
        Method method = AssetItemMapper.class.getMethod("selectPhysicallyByAssetId", Long.class);
        String sql = String.join("", method.getAnnotation(Select.class).value());

        BoundSql boundSql = bind(sql, Map.of("assetId", 12L));
        assertThat(boundSql.getSql())
                .startsWith("SELECT * FROM afv_asset_item")
                .contains("asset_id =")
                .doesNotContain("deleted = 0");
    }

    // ========== 无项目归属的历史软删行（孤儿行） ==========

    @Test
    void selectDeletedWithoutProjectScopesToNullProjectDeletedRows() throws Exception {
        Method method = AssetMapper.class.getMethod("selectDeletedWithoutProject");
        String sql = String.join("", method.getAnnotation(Select.class).value());

        BoundSql boundSql = bind(sql, Map.of());
        assertThat(boundSql.getSql())
                .contains("deleted = 1")
                .contains("project_id IS NULL")
                .doesNotContain("deleted = 0");
    }

    @Test
    void selectDeletedByIdsScopesToNullProjectAndExpandsForeach() throws Exception {
        String sql = selectSql("selectDeletedByIds", Collection.class);
        assertThat(sql.trim()).startsWith("<script>").endsWith("</script>");

        BoundSql boundSql = bind(sql, Map.of("ids", List.of(21L, 22L)));
        assertThat(boundSql.getSql())
                .contains("deleted = 1")
                .contains("project_id IS NULL")
                .contains("id IN")
                .doesNotContain("deleted = 0");
        assertThat(boundSql.getParameterMappings()).hasSize(2);
    }
}
