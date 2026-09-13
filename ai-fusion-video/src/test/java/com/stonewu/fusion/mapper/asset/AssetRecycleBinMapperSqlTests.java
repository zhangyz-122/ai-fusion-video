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
}
