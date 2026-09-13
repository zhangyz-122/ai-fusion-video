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
import java.util.List;

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

    /**
     * 查询全部已逻辑删除且无项目归属（project_id IS NULL）的历史软删行，按删除时间倒序。
     * <p>
     * 此类行无法经 project_id 做项目归属校验，由调用方按资产归属（userId/ownerId/团队）
     * 过滤出当前用户可操作的行。
     */
    @Select("SELECT * FROM afv_asset WHERE deleted = 1 AND project_id IS NULL ORDER BY update_time DESC")
    List<Asset> selectDeletedWithoutProject();

    /**
     * 按 id 集合查询已逻辑删除且无项目归属（project_id IS NULL）的历史软删行。
     * <p>
     * 供批量清理前逐行做归属校验：返回行数少于请求 id 数即存在不存在/有项目归属/未删除的行。
     * 注意：@Select 的文本块必须以 {@code <script>} 开头（顶格书写），否则动态 SQL 不生效。
     */
    @Select("""
<script>
SELECT * FROM afv_asset
WHERE deleted = 1
AND project_id IS NULL
AND id IN
<foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
</script>
""")
    List<Asset> selectDeletedByIds(@Param("ids") Collection<Long> ids);

    /** 恢复已逻辑删除的资产（置 deleted = 0），仅命中软删行，返回受影响行数 */
    @Update("UPDATE afv_asset SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 物理删除资产行（绕过逻辑删除），返回受影响行数 */
    @Delete("DELETE FROM afv_asset WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
