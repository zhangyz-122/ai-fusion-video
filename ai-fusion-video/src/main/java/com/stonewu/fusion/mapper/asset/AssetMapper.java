package com.stonewu.fusion.mapper.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.stonewu.fusion.entity.asset.Asset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

@Mapper
public interface AssetMapper extends BaseMapper<Asset> {

    /**
     * 分页查询已逻辑删除（deleted = 1）的资产，按删除时间（update_time）倒序。
     * <p>
     * 实体上的 @TableLogic 只会为 BaseMapper 注入方法自动追加 deleted = 0 过滤，
     * 此处为显式自定义 SQL，可命中软删行；projectIds 必须非空，为空时由调用方直接返回空页。
     * 注意：@Select 的文本块必须以 {@code <script>} 开头（顶格书写），否则动态 SQL 不生效。
     */
    @Select("""
<script>
SELECT * FROM afv_asset
WHERE deleted = 1
AND project_id IN
<foreach collection="projectIds" item="projectId" open="(" separator="," close=")">#{projectId}</foreach>
ORDER BY update_time DESC
</script>
""")
    IPage<Asset> selectDeletedPage(IPage<Asset> page, @Param("projectIds") Collection<Long> projectIds);

    /** 按 id 查询已逻辑删除的资产，不存在或未删除时返回 null */
    @Select("SELECT * FROM afv_asset WHERE id = #{id} AND deleted = 1")
    Asset selectDeletedById(@Param("id") Long id);

    /** 恢复已逻辑删除的资产（置 deleted = 0），仅命中软删行，返回受影响行数 */
    @Update("UPDATE afv_asset SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 物理删除资产行（绕过逻辑删除），返回受影响行数 */
    @Delete("DELETE FROM afv_asset WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
